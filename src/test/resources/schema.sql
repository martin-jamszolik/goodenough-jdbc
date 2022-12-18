CREATE TABLE contractor (
    sc_key INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
    sc_name VARCHAR(100) NOT NULL,
    contact VARCHAR(100),
    phone1 VARCHAR(40),
    fax VARCHAR(40),
    email VARCHAR(40)
)

CREATE TABLE est_proposal (
  pr_key INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
  sc_key INTEGER NOT NULL,
  proposal_name VARCHAR(45) NOT NULL,
  dist BIGINT,
  prop_date DATE,
  submit_deadline DATE,
  prop_id VARCHAR(100),
  FOREIGN KEY (sc_key) REFERENCES contractor(sc_key)
);

CREATE TABLE task (
  t_key INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
  task_name VARCHAR(45) NOT NULL,
);

CREATE TABLE proposal_task (
  t_key INTEGER NOT NULL,
  pr_key INTEGER NOT NULL,
  price BIGINT,
  FOREIGN KEY (pr_key) REFERENCES est_proposal(pr_key) ON DELETE CASCADE,
  FOREIGN KEY (t_key) REFERENCES task(t_key) ON DELETE CASCADE
);
ALTER TABLE proposal_task ADD CONSTRAINT pt_compositePK PRIMARY KEY (t_key,pr_key);
