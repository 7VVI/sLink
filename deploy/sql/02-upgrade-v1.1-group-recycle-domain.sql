-- ---------------------------------------------------------------------------
-- v1.1 升级脚本（存量环境）：新增分组/域名/回收站支持
-- - 主库新增 link_group、surl_domain 表
-- - 64 张分片表新增 group_id / domain_id / delete_time 列
-- 幂等可重复执行。
-- ---------------------------------------------------------------------------

USE short_link_main;

CREATE TABLE IF NOT EXISTS link_group (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    user_id     BIGINT       NOT NULL COMMENT '所属用户',
    name        VARCHAR(32)  NOT NULL COMMENT '分组名',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_id, name)
) ENGINE = InnoDB COMMENT '短链分组';

CREATE TABLE IF NOT EXISTS surl_domain (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    domain      VARCHAR(255) NOT NULL COMMENT '域名前缀（含协议，不带路径）',
    name        VARCHAR(64)  NULL COMMENT '备注名',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '1-默认域名',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_domain (domain)
) ENGINE = InnoDB COMMENT '短链域名';

DELIMITER $$

CREATE PROCEDURE upgrade_short_url_shards()
BEGIN
    DECLARE ds INT DEFAULT 0;
    DECLARE tb INT DEFAULT 0;
    DECLARE col_count INT DEFAULT 0;
    WHILE ds < 4 DO
        WHILE tb < 16 DO
            SELECT COUNT(*) INTO col_count FROM information_schema.columns
            WHERE table_schema = CONCAT('short_link_ds', ds)
              AND table_name = CONCAT('short_url_', tb)
              AND column_name = 'group_id';
            IF col_count = 0 THEN
                SET @ddl = CONCAT(
                    'ALTER TABLE short_link_ds', ds, '.short_url_', tb, ' ',
                    'ADD COLUMN group_id BIGINT NOT NULL DEFAULT 0 COMMENT ''分组ID（0=未分组）'' AFTER user_id, ',
                    'ADD COLUMN domain_id BIGINT NOT NULL DEFAULT 0 COMMENT ''域名ID（0=系统默认）'' AFTER group_id, ',
                    'ADD COLUMN delete_time DATETIME NULL COMMENT ''移入回收站时间'' AFTER status');
                PREPARE stmt FROM @ddl;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            END IF;
            SET tb = tb + 1;
        END WHILE;
        SET tb = 0;
        SET ds = ds + 1;
    END WHILE;
END$$

CALL upgrade_short_url_shards()$$

DROP PROCEDURE upgrade_short_url_shards$$

DELIMITER ;
