package spike;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;
public class ProbeDialect extends MySQLDialect {
    @Override public void initializeFunctionRegistry(FunctionContributions functions) {
        super.initializeFunctionRegistry(functions);
        functions.getFunctionRegistry().register("GROUP_CONCAT", new StandardSQLFunction("group_concat", StandardBasicTypes.STRING));
    }
}
