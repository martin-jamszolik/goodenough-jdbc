CREATE TABLE contractor (
    sc_key INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
    sc_name VARCHAR(100) NOT NULL,
    contact VARCHAR(100),
    phone1 VARCHAR(40),
    fax VARCHAR(40),
    email VARCHAR(40)
);

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

CREATE TABLE progress (
  id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
  percent DECIMAL (10,2) NOT NULL
);

CREATE TABLE proposal_task (
  t_key INTEGER NOT NULL,
  pr_key INTEGER NOT NULL,
  price BIGINT,
  FOREIGN KEY (pr_key) REFERENCES est_proposal(pr_key) ON DELETE CASCADE,
  FOREIGN KEY (t_key) REFERENCES task(t_key) ON DELETE CASCADE
);
ALTER TABLE proposal_task ADD CONSTRAINT pt_compositePK PRIMARY KEY (t_key,pr_key);

CREATE TABLE note (
  n_key INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
  note VARCHAR(45) NOT NULL,
  note_date DATE,
  additional VARCHAR(100),
  progress_id INTEGER,
  note_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (progress_id) REFERENCES progress(id) ON DELETE CASCADE
);

CREATE TABLE supplier (
    id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
    sup_name VARCHAR(100) NOT NULL,
    contact VARCHAR(100)
);

CREATE TABLE purchase_order (
  id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) PRIMARY KEY,
  requester VARCHAR(500) NOT NULL,
  po_number_id BIGINT,
  primitive_id BIGINT,
  long_id BIGINT,
  n_key INTEGER,
  supplier_id INTEGER,
  FOREIGN KEY (n_key) REFERENCES note(n_key) ON DELETE CASCADE,
  FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE CASCADE
);
