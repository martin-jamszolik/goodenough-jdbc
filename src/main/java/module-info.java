@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module org.viablespark.persistence {
    exports org.viablespark.persistence;
    exports org.viablespark.persistence.dsl;
    requires transitive spring.jdbc;
    requires spring.core;
    requires spring.jcl;
    requires spring.beans;
    requires spring.tx;
    requires transitive java.sql;
    requires org.slf4j;
}