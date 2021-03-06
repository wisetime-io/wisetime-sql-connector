--use master;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

DROP TABLE IF EXISTS TEST_CASES;
CREATE TABLE TEST_CASES
(
    IRN          NVARCHAR(255),
    TITLE        NVARCHAR(255),
    URL          NVARCHAR(255),
    DATE_UPDATED DATETIME
);

INSERT INTO TEST_CASES (IRN, TITLE, URL, DATE_UPDATED)
VALUES ('P0100973', 'Software for connecting SQL database with timekeeping API', 'http://www.google.com', '2019-08-06');
INSERT INTO TEST_CASES (IRN, TITLE, URL, DATE_UPDATED)
VALUES ('P0436021', 'Method and apparatus for jumping through hoops', 'http://www.google.com', '2019-07-21');
INSERT INTO TEST_CASES (IRN, TITLE, URL, DATE_UPDATED)
VALUES ('P0070709', 'Toy building brick', 'http://www.google.com', '2014-11-01');

DROP TABLE IF EXISTS TEST_PROJECTS;
CREATE TABLE TEST_PROJECTS
(
    PRJ_ID      INT NOT NULL IDENTITY(80000,1) PRIMARY KEY,
    IRN         NVARCHAR(255),
    DESCRIPTION NVARCHAR(255)
);

INSERT INTO TEST_PROJECTS (IRN, DESCRIPTION)
VALUES ('P0436021', 'Application - Draft 1');
INSERT INTO TEST_PROJECTS (IRN, DESCRIPTION)
VALUES ('P0436021', 'Application - Draft 2');
INSERT INTO TEST_PROJECTS (IRN, DESCRIPTION)
VALUES ('P0070709', 'Response');

DROP TABLE IF EXISTS TEST_TAG_METADATA;
CREATE TABLE TEST_TAG_METADATA
(
    META_ID  INT NOT NULL IDENTITY(80000,1) PRIMARY KEY,
    IRN      NVARCHAR(255),
    COUNTRY  NVARCHAR(255),
    LOCATION NVARCHAR(255)
);

INSERT INTO TEST_TAG_METADATA (IRN, COUNTRY, LOCATION)
VALUES ('P0100973', 'Germany', 'Berlin');
INSERT INTO TEST_TAG_METADATA (IRN, COUNTRY, LOCATION)
VALUES ('P0436021', 'Italy', 'Rome');
INSERT INTO TEST_TAG_METADATA (IRN, COUNTRY, LOCATION)
VALUES ('P0070709', 'Australia', 'Canberra');

DROP TABLE IF EXISTS TEST_ACTIVITYCODES;
CREATE TABLE TEST_ACTIVITYCODES (
  ACTIVITYCODE NVARCHAR(255),
  ACTIVITYNAME NVARCHAR(255),
  ACTIVITYDESCRIPTION NVARCHAR(255)
);

INSERT INTO TEST_ACTIVITYCODES (ACTIVITYCODE, ACTIVITYNAME, ACTIVITYDESCRIPTION)
VALUES ('12345', 'Billable', 'Billable description');
INSERT INTO TEST_ACTIVITYCODES (ACTIVITYCODE, ACTIVITYNAME, ACTIVITYDESCRIPTION)
VALUES ('23456', 'Non-Billable', 'Non-Billable description');
INSERT INTO TEST_ACTIVITYCODES (ACTIVITYCODE, ACTIVITYNAME, ACTIVITYDESCRIPTION)
VALUES ('34567', 'Default', 'Default description');
