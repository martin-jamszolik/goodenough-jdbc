@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module org.viablespark.persistence {
    exports org.viablespark.persistence;
    exports org.viablespark.persistence.dsl;
    requires transitive spring.jdbc;
    requires spring.core;
    requires transitive java.sql;
    requires transitive org.slf4j;
}