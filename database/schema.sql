CREATE DATABASE IF NOT EXISTS moment_sync
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE moment_sync;

-- =========================================================
-- 1. 账号与基础字典表（兼容第一周）
-- =========================================================

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  avatar_url VARCHAR(500),
  bio VARCHAR(300) DEFAULT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(30) NOT NULL UNIQUE,
  description VARCHAR(255) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_interest_tag (
  user_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, tag_id),
  CONSTRAINT fk_uit_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_uit_tag FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 2. 同频圈子表（需在 post 前创建以支持可选关联）
-- =========================================================

CREATE TABLE IF NOT EXISTS circle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(500),
  cover_url VARCHAR(500),
  owner_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_circle_owner FOREIGN KEY (owner_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS circle_member (
  circle_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (circle_id, user_id),
  CONSTRAINT fk_cm_circle FOREIGN KEY (circle_id) REFERENCES circle(id),
  CONSTRAINT fk_cm_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  INDEX idx_cm_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 3. 印记、多媒体与互动表（兼容第一周并扩展事件日期/胶囊/评分/回响/二级评论）
-- =========================================================

CREATE TABLE IF NOT EXISTS post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  mood VARCHAR(30),
  campus_location VARCHAR(100),
  event_date DATE DEFAULT NULL,
  circle_id BIGINT DEFAULT NULL,
  unlock_at DATETIME DEFAULT NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  rating_avg DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
  rating_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_post_circle FOREIGN KEY (circle_id) REFERENCES circle(id),
  INDEX idx_post_user_id (user_id),
  INDEX idx_post_circle_id (circle_id),
  INDEX idx_post_status_created (status, created_at),
  INDEX idx_post_event_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_media (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
  media_url VARCHAR(500) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES post(id),
  INDEX idx_media_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_tag (
  post_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (post_id, tag_id),
  CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post(id),
  CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT DEFAULT NULL,
  content VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post(id),
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment(id),
  INDEX idx_comment_post_created (post_id, created_at),
  INDEX idx_comment_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_rating (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  score TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rating_post_user (post_id, user_id),
  CONSTRAINT fk_rating_post FOREIGN KEY (post_id) REFERENCES post(id),
  CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_reaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  reaction_type VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reaction_post_user_type (post_id, user_id, reaction_type),
  CONSTRAINT fk_reaction_post FOREIGN KEY (post_id) REFERENCES post(id),
  CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  INDEX idx_reaction_user_type (user_id, reaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 4. 关注、好友关系与记忆故事集（含好友共建）
-- =========================================================

CREATE TABLE IF NOT EXISTS user_follow (
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, followee_id),
  CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES `user`(id),
  CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES `user`(id),
  INDEX idx_follow_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friend_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  message VARCHAR(200) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_freq_sender FOREIGN KEY (sender_id) REFERENCES `user`(id),
  CONSTRAINT fk_freq_receiver FOREIGN KEY (receiver_id) REFERENCES `user`(id),
  INDEX idx_freq_receiver_status (receiver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friendship (
  user_id BIGINT NOT NULL,
  friend_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, friend_id),
  CONSTRAINT fk_friendship_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_friendship_friend FOREIGN KEY (friend_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_collection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  cover_url VARCHAR(500) DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_story_owner FOREIGN KEY (owner_id) REFERENCES `user`(id),
  INDEX idx_story_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_collaborator (
  story_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (story_id, user_id),
  CONSTRAINT fk_sc_story FOREIGN KEY (story_id) REFERENCES story_collection(id),
  CONSTRAINT fk_sc_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_post (
  story_id BIGINT NOT NULL,
  post_id BIGINT NOT NULL,
  added_by BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (story_id, post_id),
  CONSTRAINT fk_sp_story FOREIGN KEY (story_id) REFERENCES story_collection(id),
  CONSTRAINT fk_sp_post FOREIGN KEY (post_id) REFERENCES post(id),
  CONSTRAINT fk_sp_added_by FOREIGN KEY (added_by) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 5. 即时通讯（好友单聊 + 拉群聊）、校园搭子与站内通知
-- =========================================================

CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
  direct_key VARCHAR(64) DEFAULT NULL UNIQUE,
  name VARCHAR(100) DEFAULT NULL,
  owner_id BIGINT DEFAULT NULL,
  avatar_url VARCHAR(500) DEFAULT NULL,
  last_message_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_owner FOREIGN KEY (owner_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_member (
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  last_read_message_id BIGINT NOT NULL DEFAULT 0,
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conversation_id, user_id),
  CONSTRAINT fk_chatm_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id),
  CONSTRAINT fk_chatm_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  INDEX idx_chatm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  content TEXT NOT NULL,
  ref_post_id BIGINT DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SENT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id),
  CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES `user`(id),
  INDEX idx_msg_conv_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS buddy_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  category VARCHAR(30) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL,
  campus_location VARCHAR(100) DEFAULT NULL,
  event_time DATETIME DEFAULT NULL,
  max_members INT NOT NULL DEFAULT 2,
  conversation_id BIGINT DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_buddy_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_buddy_conv FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id),
  INDEX idx_buddy_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS buddy_signup (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  buddy_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  note VARCHAR(200) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'JOINED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_buddy_user (buddy_id, user_id),
  CONSTRAINT fk_bs_buddy FOREIGN KEY (buddy_id) REFERENCES buddy_post(id),
  CONSTRAINT fk_bs_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  actor_id BIGINT DEFAULT NULL,
  type VARCHAR(30) NOT NULL,
  target_type VARCHAR(30) DEFAULT NULL,
  target_id BIGINT DEFAULT NULL,
  content VARCHAR(300) NOT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_notif_actor FOREIGN KEY (actor_id) REFERENCES `user`(id),
  INDEX idx_notif_user_read_created (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 6. 举报审核、动态敏感词库与后台操作审计日志
-- =========================================================

CREATE TABLE IF NOT EXISTS content_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  circle_id BIGINT DEFAULT NULL,
  reason_category VARCHAR(30) NOT NULL,
  reason_detail VARCHAR(300) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  handler_id BIGINT DEFAULT NULL,
  handle_note VARCHAR(300) DEFAULT NULL,
  handled_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES `user`(id),
  CONSTRAINT fk_report_circle FOREIGN KEY (circle_id) REFERENCES circle(id),
  CONSTRAINT fk_report_handler FOREIGN KEY (handler_id) REFERENCES `user`(id),
  INDEX idx_report_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sensitive_word (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(60) NOT NULL UNIQUE,
  category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sw_creator FOREIGN KEY (created_by) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT NOT NULL,
  action VARCHAR(50) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  detail VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES `user`(id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
