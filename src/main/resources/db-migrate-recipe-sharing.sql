CREATE TABLE IF NOT EXISTS recipe_shares (
  share_code CHAR(12) NOT NULL,
  recipe_id BIGINT NOT NULL,
  user_openid VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (share_code),
  KEY idx_recipe_share_owner_recipe (user_openid, recipe_id),
  KEY idx_recipe_share_recipe (recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
