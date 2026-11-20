CREATE TABLE `attachments` (
	`trip_id` text NOT NULL,
	`id` text NOT NULL,
	`block_id` text NOT NULL,
	`file_name` text NOT NULL,
	`mime_type` text NOT NULL,
	`size_bytes` integer NOT NULL,
	`sha256` text NOT NULL,
	`data` blob NOT NULL,
	`rev` integer NOT NULL,
	`created_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `attachments_rev` ON `attachments` (`trip_id`,`rev`);--> statement-breakpoint
CREATE INDEX `attachments_block` ON `attachments` (`trip_id`,`block_id`);--> statement-breakpoint
CREATE TABLE `connector_keys` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`name` text NOT NULL,
	`key_hash` text NOT NULL,
	`created_at` integer NOT NULL,
	`last_used_at` integer,
	`revoked_at` integer,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `connector_keys_key_hash_unique` ON `connector_keys` (`key_hash`);--> statement-breakpoint
CREATE INDEX `connector_keys_user` ON `connector_keys` (`user_id`);--> statement-breakpoint
CREATE TABLE `upload_links` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`trip_id` text NOT NULL,
	`block_id` text NOT NULL,
	`expires_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
