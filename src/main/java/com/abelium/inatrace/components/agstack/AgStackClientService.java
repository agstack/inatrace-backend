package com.abelium.inatrace.components.agstack;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.agstack.api.ApiRegisterFieldBoundaryErrorResponse;
import com.abelium.inatrace.components.agstack.api.ApiRegisterFieldBoundaryRequest;
import com.abelium.inatrace.components.agstack.api.ApiRegisterFieldBoundaryResponse;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.time.Duration;

@Service
public class AgStackClientService {

	@Value("${INATrace.agstack.baseURL}")
	private String baseURL;

	@Value("${INATrace.agstack.requestTimeoutMs:30000}")
	private long requestTimeoutMs = 30000;

    private final AgStackClientTokenManager tokenService;

    public AgStackClientService(AgStackClientTokenManager tokenService) {
        this.tokenService = tokenService;
    }

	public ApiRegisterFieldBoundaryResponse registerFieldBoundaryResponse(List<PlotCoordinate> plotCoordinates) throws ApiException {

		ApiRegisterFieldBoundaryRequest request = new ApiRegisterFieldBoundaryRequest();
		request.setS2Index("8, 13");

		StringBuilder stringBuilder = new StringBuilder();

		for (int i = 0; i < plotCoordinates.size(); i++) {
			stringBuilder
					.append(plotCoordinates.get(i).getLongitude()).append(" ")
					.append(plotCoordinates.get(i).getLatitude());
			if (i < plotCoordinates.size() - 1) {
				stringBuilder.append(", ");
			}
		}
		request.setWkt("POLYGON ((" + stringBuilder + "))");

        String token = this.tokenService.retrieveToken();
        if (token == null) {
            throw new ApiException(ApiStatus.ERROR, "Error while retrieving api token");
        }

		WebClient webClient = WebClient.create(baseURL);

		return webClient
				.post()
				.uri(uriBuilder -> uriBuilder.path("/register-field-boundary").build())
				.body(Mono.just(request), ApiRegisterFieldBoundaryRequest.class)
				.header("Authorization", "Bearer " + token)
				.header("X-FROM-ASSET-REGISTRY", "True")
				.accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                        tokenService.invalidateToken(token);
                    }
                    // 1. Handle 400 BAD REQUEST, because of api implementation,
                    // In this request "matched geo ids" can be returned, with error message
                    // so it is mapped in response class
                    if (response.statusCode().equals(HttpStatus.BAD_REQUEST)) {
                        return response.bodyToMono(ApiRegisterFieldBoundaryResponse.class)
                                .switchIfEmpty(Mono.error(new ApiException(ApiStatus.ERROR,
                                        "AgStack boundary request failed with HTTP 400")));
                    }
                    // 2. Handle 200 OK (and other success codes)
                    else if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(ApiRegisterFieldBoundaryResponse.class);
                    }
                    // 3. Handle errors, throw exception
                    else {
                        return response.bodyToMono(ApiRegisterFieldBoundaryErrorResponse.class)
                                .flatMap(error -> Mono.<ApiRegisterFieldBoundaryResponse>error(
                                        new ApiException(ApiStatus.ERROR, error.getError())))
                                .switchIfEmpty(Mono.error(new ApiException(ApiStatus.ERROR,
                                        "AgStack boundary request failed with HTTP " + response.statusCode().value())));
                    }
                })
				.timeout(Duration.ofMillis(requestTimeoutMs))
				.block();
	}

}
