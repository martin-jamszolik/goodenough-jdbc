@SuppressWarnings({"requires-automatic", "requires-transitive-automatic"})
module org.viablespark.persistence {
  exports org.viablespark.persistence;
  exports org.viablespark.persistence.dsl;

  requires spring.jdbc;
  requires spring.core;
  requires spring.beans;
  requires transitive java.sql;
  requires org.slf4j;
}
