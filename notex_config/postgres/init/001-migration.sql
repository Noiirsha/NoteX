CREATE EXTENSION IF NOT EXISTS vector;

-- CreateTable
CREATE TABLE "user_base" (
    "id" BIGSERIAL NOT NULL,
    "email" TEXT NOT NULL,
    "username" TEXT NOT NULL,
    "nickname" TEXT NOT NULL,
    "password_hash" TEXT NOT NULL,
    "avatar_image_url" TEXT,
    "register_time" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "last_login_time" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_base_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_preferences" (
    "id" BIGSERIAL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "dark_mode" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "user_preferences_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_ai_models" (
    "id" BIGSERIAL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "standard_model_base" TEXT,
    "standard_model_api_key" TEXT,
    "standard_model_model_name" TEXT,
    "embedded_model_base" TEXT,
    "embedded_model_api_key" TEXT,
    "embedded_model_model_name" TEXT,

    CONSTRAINT "user_ai_models_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "note_group" (
    "id" BIGSERIAL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "group_name" TEXT NOT NULL,
    "parent_group_id" BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT "note_group_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "note" (
    "id" BIGSERIAL NOT NULL,
    "group_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL,
    "title" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "note_uuid" TEXT NOT NULL,
    "content" TEXT,
    "is_indexed" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "note_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_chats" (
    "id" BIGSERIAL NOT NULL,
    "user_id" BIGINT NOT NULL,
    "conversation_id" TEXT NOT NULL,
    "conversation_title" TEXT NOT NULL DEFAULT '新对话',
    "last_access_time" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_chats_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "chat_history" (
    "id" BIGSERIAL NOT NULL,
    "conversation_id" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "chat_history_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "user_base_email_key" ON "user_base"("email");

-- CreateIndex
CREATE UNIQUE INDEX "user_base_username_key" ON "user_base"("username");

-- CreateIndex
CREATE INDEX "user_base_username_idx" ON "user_base"("username");

-- CreateIndex
CREATE UNIQUE INDEX "user_preferences_user_id_key" ON "user_preferences"("user_id");

-- CreateIndex
CREATE UNIQUE INDEX "user_ai_models_user_id_key" ON "user_ai_models"("user_id");

-- CreateIndex
CREATE INDEX "note_group_user_id_parent_group_id_idx" ON "note_group"("user_id", "parent_group_id");

-- CreateIndex
CREATE UNIQUE INDEX "note_note_uuid_key" ON "note"("note_uuid");

-- CreateIndex
CREATE INDEX "note_note_uuid_idx" ON "note"("note_uuid");

-- CreateIndex
CREATE INDEX "user_chats_conversation_id_idx" ON "user_chats"("conversation_id");

-- CreateIndex
CREATE INDEX "chat_history_conversation_id_idx" ON "chat_history"("conversation_id");

-- AddForeignKey
ALTER TABLE "user_preferences" ADD CONSTRAINT "user_preferences_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user_base"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_ai_models" ADD CONSTRAINT "user_ai_models_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user_base"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "note_group" ADD CONSTRAINT "note_group_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user_base"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "note" ADD CONSTRAINT "note_group_id_fkey" FOREIGN KEY ("group_id") REFERENCES "note_group"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "note" ADD CONSTRAINT "note_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user_base"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_chats" ADD CONSTRAINT "user_chats_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "user_base"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
