package com.abelium.inatrace.components.mail;

import com.abelium.inatrace.configuration.ApiRequestObservability;
import com.abelium.inatrace.configuration.SafeExceptionLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;


@Lazy
@Component
public class MailEngine
{
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MailEngine.class);

    /**
     * Never null. When no SMTP server is configured, Spring Boot does not create a
     * JavaMailSenderImpl at all, and this falls back to an unconfigured instance so the
     * application can still start. An unconfigured sender builds messages perfectly well;
     * it only fails if something actually tries to transmit one, which
     * {@code INATrace.mail.sendingEnabled} already prevents.
     */
    private final JavaMailSenderImpl mailSender;

    /** Whether the sender above came from configuration rather than the fallback. */
    private final boolean mailSenderConfigured;
    
    @Value("${INATrace.mail.template.from}")
    private String fromAddress;

    @Value("${INATrace.mail.redirect}")
    private String redirectAddress;
    
    @Value("${INATrace.mail.sendingEnabled}")
    private boolean sendingEnabled;
    

    public MailEngine(ObjectProvider<JavaMailSenderImpl> mailSenderProvider) {
        JavaMailSenderImpl configured = mailSenderProvider.getIfAvailable();
        this.mailSenderConfigured = configured != null;
        this.mailSender = configured != null ? configured : new JavaMailSenderImpl();
    }

    /**
     * Refuse to start if mail sending is switched on but there is nothing to send it with.
     * Leaving that combination to fail later would swallow every message silently.
     */
    @PostConstruct
    void verifyMailConfiguration() {
        if (mailSenderConfigured) {
            return;
        }
        if (sendingEnabled) {
            throw new IllegalStateException(
                    "INATrace.mail.sendingEnabled is true but no SMTP server is configured. "
                            + "Set spring.mail.host (and spring.mail.port), or set "
                            + "INATrace.mail.sendingEnabled = false.");
        }
        logger.warn("No SMTP server configured (spring.mail.host is not set); mail sending is off. "
                + "Messages will be built but never transmitted.");
    }

    /**
     * Asynchronously send a single part plaintext or html email without {@code cc} or {@code bcc} destinations.
     * If exception happens during message construction or sending, it is logged but nothing is thrown.   
     * @param to comma separated mail destination
     * @param subject mail subject
     * @param plainText plain text for message body
     * @param htmlText html text for message body
     * @param html if {@code true}, body is html
     * @return a completable future that is fulfilled with message ID when mail is sent and failed if error occurs
     */
    public CompletableFuture<String> sendSimpleMailAsync(String from, String to, String bcc, String subject, String plainText, String htmlText) {
        return sendMailAsync(() -> createSimpleMail(from, to, bcc, subject, plainText, htmlText));
    }

    public CompletableFuture<String> sendSimpleMailAsync(String to, String bcc, String subject, String plainText, String htmlText) {
        return sendMailAsync(() -> createSimpleMail(null, to, bcc, subject, plainText, htmlText));
    }

    public MimeMessageHelper createSimpleMail(String from, String to, String bcc, String subject, String plainText, String htmlText) throws MessagingException {
        MimeMessageHelper helper = createMessageHelper(true);
        if (from != null) helper.setFrom(from);
        helper.setTo(MailUtils.parseAddresses(helper, to));
        if (StringUtils.isNotBlank(bcc)) {
            helper.setBcc(MailUtils.parseAddresses(helper, bcc));
        }
        helper.setSubject(subject);
        helper.setText(plainText, htmlText);
        MailUtils.addHtmlEmailImages(helper);
        return helper;
    }
    
    public CompletableFuture<String> sendSimplePlainTextMailAsync(String to, String subject, String plainText) {
        return sendMailAsync(() -> createSimplePlainTextMail(null, to, subject, plainText));
    }
    
    public MimeMessageHelper createSimplePlainTextMail(String from, String to, String subject, String plainText) throws MessagingException {
    	MimeMessageHelper helper = createMessageHelper(true);
        if (from != null) helper.setFrom(from);
        helper.setTo(MailUtils.parseAddresses(helper, to));
        helper.setSubject(subject);
        helper.setText(plainText);
        return helper;
    }

    /**
     * Create a new message helper to construct complex email. 
     * @return a new message helper
     * @throws MessagingException if something goes wrong during construction (e.g. {@code from} address is unparsable)
     */
    public MimeMessageHelper createMessageHelper() throws MessagingException {
        return createMessageHelper(false);
    }
    
    /**
     * Create a new message helper to construct complex email.
     * @param multipart if true, create multipart message (with attachments or plaintext and html) 
     * @returna new message helper
     * @throws MessagingException if something goes wrong during construction (e.g. {@code from} address is unparsable)
     */
    public MimeMessageHelper createMessageHelper(boolean multipart) throws MessagingException {
        MimeMessage mail = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mail, multipart);
        helper.setFrom(fromAddress);
        return helper;
    }
    
    /**
     * Send a mail. May take some time (cca 2-10 seconds), so you might consider using async version. 
     * @param message constructed message of type {@link MimeMessageHelper}
     * @return the message ID that can be used for tracking mail sending success or rejection
     * @throws MessagingException if anything goes wrong whe handling message body
     * @throws MailSendingException if anything goes wrong during sending
     */
    public String sendMail(MimeMessageHelper message) throws MessagingException, MailSendingException {
        if ( StringUtils.isNotBlank(redirectAddress) )
            redirectMail(message.getMimeMessage(), redirectAddress);
        return sendMailWithoutRedirection(message);
    }
    
	public String sendMailWithoutRedirection(MimeMessageHelper message) throws MessagingException, MailSendingException {
		if (!sendingEnabled)
			return "";

		MimeMessage mimeMessage = message.getMimeMessage();
		sendMimeMessageWrapper(mimeMessage);
		logger.debug("Message sent request_id={}", ApiRequestObservability.requestId());
		return mimeMessage.getMessageID();
	}

    private void sendMimeMessageWrapper(MimeMessage mimeMessage) throws MailSendingException {
        try {
            mailSender.send(mimeMessage);
        } catch (MailException exc) {
            throw new MailSendingException("Error sending email", exc);
        }
    }

    /**
     * Asynchronously send an email message.
     * If exception happens during sending, it is logged but nothing is thrown.
     * @param message constructed message of type {@code MimeMessageHelper}
     * @return a completable future that is fulfilled with message ID when mail is sent and failed if error occurs
     */
    public CompletableFuture<String> sendMailAsync(MimeMessageHelper message) {
        return sendMailAsync(() -> message);
    }
    
    /**
     * Asynchronously construct and send an email message.
     * If exception happens during message construction or sending, it is logged but nothing is thrown.
     * @param builder a function that constructs and returns an {@code MimeMessageHelper}
     * @return a completable future that is fulfilled with message ID when mail is sent and failed if error occurs
     */
	public CompletableFuture<String> sendMailAsync(MimeMessageBuilder builder) {
		String requestId = ApiRequestObservability.requestId();
		return CompletableFuture.supplyAsync(() -> {
			Map<String, String> previous = MDC.getCopyOfContextMap();
			try {
				if ("none".equals(requestId)) MDC.remove(ApiRequestObservability.MDC_KEY);
				else MDC.put(ApiRequestObservability.MDC_KEY, requestId);
				MimeMessageHelper helper = builder.build();
				return sendMail(helper);
			} catch (MailSendingException e) {
				// alert was already sent, so don't log anything here
				throw new CompletionException(e);
			} catch (MessagingException e) {
				SafeExceptionLog.error(logger, "Error building or sending email", e);
				throw new CompletionException(e);
			} catch (RuntimeException e) {
				SafeExceptionLog.error(logger, "Unknown error building or sending email", e);
				throw e;
			} finally {
				if (previous == null) MDC.clear();
				else MDC.setContextMap(previous);
			}
		}, executorService);
	}
    
    /**
     * Wait at most 1 minute for async emails to be sent and then stop this engine.
     * May be used before program terminates to prevent mails to be lost.
     */
	public void shutdownAndWait() {
		try {
			executorService.shutdown();
			executorService.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			logger.warn("Async service wait interrupted, {} mails have been dropped", getMailsInQueue());
			Thread.currentThread().interrupt();
		}
	}

    private static void redirectMail(MimeMessage message, String redirectAddress) throws MessagingException {
        String originalRecipientsInfo = originalRecipientsInfo(message);
        message.setSubject(originalRecipientsInfo);
        message.setRecipients(RecipientType.TO, redirectAddress);
        message.setRecipients(RecipientType.CC, (Address[]) null);
        message.setRecipients(RecipientType.BCC, (Address[]) null);
    }
    
	private static String originalRecipientsInfo(MimeMessage message) {
		String subject = "Message";
		try {
			subject = message.getSubject();
			RecipientType[] types = { RecipientType.TO, RecipientType.CC, RecipientType.BCC };
			List<String> recipientStrs = new ArrayList<String>();
			for (RecipientType type : types) {
				Address[] recipients = message.getRecipients(type);
				if (recipients == null || recipients.length == 0)
					continue;
				recipientStrs.add(type.toString().toUpperCase() + ": " + StringUtils.join(recipients, ", "));
			}
			return subject + "  [" + StringUtils.join(recipientStrs, ";  ") + "]";
		} catch (Exception e) {
			SafeExceptionLog.error(logger, "Error getting original recipients", e);
			return subject + "  [redirected]";
		}
	}
    
    private BlockingQueue<Runnable> executorQueue = new LinkedBlockingQueue<>(10000);
    private ExecutorService executorService = createExecutorService(executorQueue);
    
    private ExecutorService createExecutorService(BlockingQueue<Runnable> workQueue) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, workQueue);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
    
    private int getMailsInQueue() {
        return executorQueue.size();
    }
}
