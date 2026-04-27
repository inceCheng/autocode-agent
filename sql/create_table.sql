create database if not exists yu_ai_code_mother;

use yu_ai_code_mother;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
    ) comment '用户' collate = utf8mb4_unicode_ci;


-- 应用表
create table app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    currentVersionId bigint                         null comment '当前成功版本 id',
    currentTaskId varchar(64)                       null comment '当前执行中的任务 id',
    generateStatus varchar(32)                      null comment '当前生成/编辑状态快照',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;


-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     longtext                           not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;


-- 切换到对应的数据库
USE yu_ai_code_mother;

-- 为 app 表增加 previewPath 列
ALTER TABLE app
    ADD COLUMN previewPath varchar(512) NULL COMMENT '预览路径' AFTER deployKey;

create table ai_generation_task (
                                    id bigint primary key auto_increment,

                                    taskId varchar(64) not null unique,
                                    appId bigint not null,
                                    userId bigint not null,

                                    projectType varchar(32) not null,
                                    status varchar(32) not null,

                                    retryCount int not null default 0,
                                    maxRetryCount int not null default 3,

                                    errorCode varchar(64) null,
                                    errorMessage varchar(1000) null,

                                    requestPayload json null,
                                    resultContent longtext null,

                                    createdAt datetime not null,
                                    updatedAt datetime not null,
                                    startedAt datetime null,
                                    finishedAt datetime null,

                                    version int not null default 0,

                                    index idx_app_id (appId),
                                    index idx_user_id (userId),
                                    index idx_status_updated_at (status, updatedAt)
);

-- 添加字段
ALTER TABLE ai_generation_task
    ADD COLUMN taskType VARCHAR(32) NOT NULL DEFAULT 'GENERATE',
    ADD COLUMN baseVersionId BIGINT NULL,
    ADD COLUMN targetVersionId BIGINT NULL;

-- 添加索引
CREATE INDEX idx_app_status ON ai_generation_task (appId, status);


create table if not exists app_version
(
    id              bigint auto_increment comment 'id' primary key,
    appId           bigint                             not null comment '应用 id',
    parentVersionId bigint                             null comment '父版本 id',
    versionNo       int                                not null comment '版本号，从 1 开始',
    taskId          varchar(64)                        not null comment '创建该版本的任务 id',
    codeGenType     varchar(64)                        not null comment '代码生成类型',
    sourcePath      varchar(512)                       not null comment '相对 CODE_OUTPUT_ROOT_DIR 的源码目录',
    manifestPath    varchar(512)                       null comment '元素 manifest 相对路径',
    previewUrl      varchar(1024)                      null comment '相对 Java API 的预览 URL',
    status          varchar(32)                        not null comment 'PENDING/SUCCESS/FAILED',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_taskId (taskId),
    UNIQUE KEY uk_app_version_no (appId, versionNo),
    INDEX idx_app_id (appId),
    INDEX idx_parent_version_id (parentVersionId)
) comment '应用代码版本' collate = utf8mb4_unicode_ci;


-- 添加字段并添加注释
ALTER TABLE app
    ADD COLUMN currentVersionId BIGINT NULL COMMENT '当前成功版本 id',
    ADD COLUMN currentTaskId VARCHAR(64) NULL COMMENT '当前执行中的任务 id',
    ADD COLUMN generateStatus VARCHAR(32) NULL COMMENT '当前生成/编辑状态快照';