-- 供本地已运行过第一周 schema.sql 且希望保留原有测试数据的同学执行：
-- 先运行本脚本补齐原表新增列，再运行 database/schema.sql 与 database/seed.sql 即可。

USE moment_sync;

ALTER TABLE `user` ADD COLUMN bio VARCHAR(300) DEFAULT NULL AFTER avatar_url;
ALTER TABLE tag ADD COLUMN description VARCHAR(255) DEFAULT NULL AFTER name;

ALTER TABLE post
  ADD COLUMN event_date DATE DEFAULT NULL AFTER campus_location,
  ADD COLUMN circle_id BIGINT DEFAULT NULL AFTER event_date,
  ADD COLUMN unlock_at DATETIME DEFAULT NULL AFTER circle_id,
  ADD COLUMN rating_avg DECIMAL(3, 2) NOT NULL DEFAULT 0.00 AFTER status,
  ADD COLUMN rating_count INT NOT NULL DEFAULT 0 AFTER rating_avg,
  ADD INDEX idx_post_circle_id (circle_id),
  ADD INDEX idx_post_event_date (event_date);

ALTER TABLE comment
  ADD COLUMN parent_id BIGINT DEFAULT NULL AFTER user_id,
  ADD INDEX idx_comment_parent_id (parent_id);
