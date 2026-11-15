CREATE TABLE `applied_mutations` (
	`trip_id` text NOT NULL,
	`mutation_id` text NOT NULL,
	`applied_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `mutation_id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `contextual_items` (
	`trip_id` text NOT NULL,
	`id` text NOT NULL,
	`linked_block_id` text,
	`link_source` text,
	`day_id` text,
	`sort_order` integer NOT NULL,
	`category` text NOT NULL,
	`title` text NOT NULL,
	`shop_or_venue_name` text,
	`price_benchmark_note` text,
	`price_source` text,
	`estimated_cost_local` real,
	`lat` real,
	`lng` real,
	`geo_verified` integer NOT NULL,
	`google_place_id` text,
	`distance_from_block_meters` integer,
	`local_script_text` text,
	`phonetic_pronunciation` text,
	`is_checked` integer NOT NULL,
	`rev` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `contextual_items_rev` ON `contextual_items` (`trip_id`,`rev`);--> statement-breakpoint
CREATE TABLE `devices` (
	`id` text PRIMARY KEY NOT NULL,
	`trip_id` text NOT NULL,
	`user_id` text NOT NULL,
	`install_id` text NOT NULL,
	`name` text NOT NULL,
	`platform` text NOT NULL,
	`app_version` text NOT NULL,
	`token_hash` text NOT NULL,
	`created_at` integer NOT NULL,
	`last_seen_at` integer,
	`revoked_at` integer,
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `devices_token_hash_unique` ON `devices` (`token_hash`);--> statement-breakpoint
CREATE INDEX `devices_user` ON `devices` (`user_id`);--> statement-breakpoint
CREATE TABLE `pair_attempts` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`ip` text NOT NULL,
	`at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `pair_attempts_ip` ON `pair_attempts` (`ip`,`at`);--> statement-breakpoint
CREATE TABLE `pairing_codes` (
	`code_hash` text PRIMARY KEY NOT NULL,
	`trip_id` text NOT NULL,
	`user_id` text NOT NULL,
	`expires_at` integer NOT NULL,
	`used_at` integer,
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `timeline_blocks` (
	`trip_id` text NOT NULL,
	`id` text NOT NULL,
	`day_id` text,
	`sort_order` integer NOT NULL,
	`start_minute` integer,
	`duration_minutes` integer NOT NULL,
	`is_fixed_time` integer NOT NULL,
	`block_type` text NOT NULL,
	`title` text NOT NULL,
	`subtitle_or_tip` text,
	`lat` real,
	`lng` real,
	`google_place_id` text,
	`google_maps_uri` text,
	`estimated_cost_local` real,
	`booking_ref` text,
	`opens_minute` integer,
	`closes_minute` integer,
	`local_script_name` text,
	`phonetic_pronunciation` text,
	`status` text NOT NULL,
	`rev` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `timeline_blocks_rev` ON `timeline_blocks` (`trip_id`,`rev`);--> statement-breakpoint
CREATE TABLE `tombstones` (
	`trip_id` text NOT NULL,
	`entity` text NOT NULL,
	`entity_id` text NOT NULL,
	`rev` integer NOT NULL,
	`deleted_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `entity`, `entity_id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `tombstones_rev` ON `tombstones` (`trip_id`,`rev`);--> statement-breakpoint
CREATE TABLE `trip_days` (
	`trip_id` text NOT NULL,
	`id` text NOT NULL,
	`day_number` integer NOT NULL,
	`date_iso` text NOT NULL,
	`timezone` text NOT NULL,
	`rev` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `trip_days_rev` ON `trip_days` (`trip_id`,`rev`);--> statement-breakpoint
CREATE TABLE `trips` (
	`id` text PRIMARY KEY NOT NULL,
	`owner_id` text NOT NULL,
	`title` text NOT NULL,
	`destination_label` text NOT NULL,
	`destination_timezone` text NOT NULL,
	`home_currency` text NOT NULL,
	`local_currency` text NOT NULL,
	`exchange_rate` real NOT NULL,
	`start_date` text NOT NULL,
	`end_date` text NOT NULL,
	`wake_minute` integer DEFAULT 450 NOT NULL,
	`rev` integer DEFAULT 0 NOT NULL,
	`meta_rev` integer DEFAULT 0 NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `trips_owner_id_unique` ON `trips` (`owner_id`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`email` text NOT NULL,
	`name` text NOT NULL,
	`password_hash` text NOT NULL,
	`role` text DEFAULT 'USER' NOT NULL,
	`disabled` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_email_unique` ON `users` (`email`);