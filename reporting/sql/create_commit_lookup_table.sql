CREATE TABLE RIODS.GIT_COMMIT_LOOKUP(
	REQUEST_ID INTEGER,	
	COMMIT_ID INTEGER,
	CONSTRAINT GIT_COMMIT_LOOKUP_PK PRIMARY KEY (REQUEST_ID,COMMIT_ID),
	CONSTRAINT GIT_COMMIT_LOOKUP_GIT_COMMIT_FK FOREIGN KEY (COMMIT_ID) REFERENCES RIODS.GIT_COMMIT(ID_PK),
	CONSTRAINT GIT_COMMIT_LOOKUP_REQUEST_FK FOREIGN KEY (REQUEST_ID) REFERENCES RIODS.REQUEST(REQUEST_ID)
);
