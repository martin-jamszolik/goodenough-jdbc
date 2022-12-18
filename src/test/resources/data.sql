INSERT INTO contractor
(sc_key,sc_name,contact,phone1,fax,email)
VALUES(1,'Mr Contractor','Main Contact','917999001122','917998921111','test@gmail.com')

INSERT INTO contractor
(sc_key,sc_name,contact,phone1,fax,email)
VALUES(2,'ABC Contractor Inc','Billy Boy','917999001134','917998921123','billyboy@gmail.com')

INSERT INTO est_proposal
(proposal_name, dist, prop_date, submit_deadline, prop_id,sc_key)
VALUES('proposal name', 123, curdate(), curdate(), 'ID1',1);

INSERT INTO est_proposal
(proposal_name, dist, prop_date, submit_deadline, prop_id,sc_key)
VALUES('proposal 2', 342, curdate(), curdate(), 'ID2',1);

INSERT INTO est_proposal
(proposal_name, dist, prop_date, submit_deadline, prop_id,sc_key)
VALUES('proposal 3', 456, curdate(), curdate(), 'ID3',2);

INSERT INTO task (t_key,task_name) VALUES(1, 'task 1');
INSERT INTO task (t_key,task_name) VALUES(2, 'task 2');
INSERT INTO task (t_key,task_name) VALUES(3, 'task 3');
INSERT INTO task (t_key,task_name) VALUES(4, 'task 4');

INSERT INTO proposal_task (t_key,pr_key,price ) VALUES (1,1,200);
INSERT INTO proposal_task (t_key,pr_key,price ) VALUES (2,1,300);