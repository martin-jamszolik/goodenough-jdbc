INSERT INTO contractor
(sc_key,sc_name,contact,phone1,fax,email)
VALUES(1,'Mr Contractor','Main Contact','917999001122','917998921111','test@gmail.com')

INSERT INTO est_proposal
(proposal_name, dist, prop_date, submit_deadline, prop_id,sc_key)
VALUES('proposal name', 123, curdate(), curdate(), 'ID1',1);

INSERT INTO est_proposal
(proposal_name, dist, prop_date, submit_deadline, prop_id,sc_key)
VALUES('proposal 2', 342, curdate(), curdate(), 'ID2',1);

