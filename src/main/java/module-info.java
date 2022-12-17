module org.viablespark.persistence {
    exports org.viablespark.persistence;
    exports org.viablespark.persistence.dsl;

    requires spring.jdbc;
    requires spring.core;
    requires java.sql;
    requires org.slf4j;

}