CREATE TABLE users
(
  id BIGINT PRIMARY KEY NOT NULL AUTO_INCREMENT,
  create_date DATETIME NOT NULL,
  hidden BIT NOT NULL,
  update_date DATETIME NOT NULL,
  activated BIT NOT NULL,
  avatar_path VARCHAR(250),
  email VARCHAR(100),
  nick_name VARCHAR(32) NOT NULL,
  phone VARCHAR(32),
  password VARCHAR(250),
  birthday DATETIME,
  token VARCHAR(250),
  refer BIGINT,
  invite_code VARCHAR(32)
);