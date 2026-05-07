Доступ к БД: localhost 5432 user:user password:password 

Схема БД:
-- DROP SCHEMA public;

CREATE SCHEMA public AUTHORIZATION pg_database_owner;

COMMENT ON SCHEMA public IS 'standard public schema';

-- DROP SEQUENCE departments_id_seq;

CREATE SEQUENCE departments_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 2147483647
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE geozone_event_config_id_seq;

CREATE SEQUENCE geozone_event_config_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 2147483647
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE geozone_events_id_seq;

CREATE SEQUENCE geozone_events_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE geozones_id_seq;

CREATE SEQUENCE geozones_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE telemetry_event_config_id_seq;

CREATE SEQUENCE telemetry_event_config_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 2147483647
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE telemetry_events_id_seq;

CREATE SEQUENCE telemetry_events_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE telemetry_packets_id_seq;

CREATE SEQUENCE telemetry_packets_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE telemetry_subrecords_id_seq;

CREATE SEQUENCE telemetry_subrecords_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;
-- DROP SEQUENCE vehicles_id_seq;

CREATE SEQUENCE vehicles_id_seq
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1
NO CYCLE;-- public.departments определение

-- Drop table

-- DROP TABLE departments;

CREATE TABLE departments ( id int4 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1 NO CYCLE) NOT NULL, "name" varchar(100) NOT NULL, CONSTRAINT departments_pkey PRIMARY KEY (id));


-- public.general_settings определение

-- Drop table

-- DROP TABLE general_settings;

CREATE TABLE general_settings ( "key" varchar(50) NOT NULL, value text NOT NULL, description text NULL, CONSTRAINT general_settings_pkey PRIMARY KEY (key));


-- public.geozones определение

-- Drop table

-- DROP TABLE geozones;

CREATE TABLE geozones ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, "name" text NOT NULL, "type" varchar(10) NOT NULL, coordinates jsonb NOT NULL, is_active bool DEFAULT true NOT NULL, s2_key int8 DEFAULT 0 NOT NULL, lat float8 DEFAULT 0 NOT NULL, lon float8 DEFAULT 0 NOT NULL, CONSTRAINT geozones_pkey PRIMARY KEY (id), CONSTRAINT geozones_type_check CHECK (((type)::text = ANY ((ARRAY['polygon'::character varying, 'circle'::character varying])::text[]))));
CREATE INDEX geozones_s2_key_idx ON public.geozones USING btree (s2_key);


-- public.telemetry_event_config определение

-- Drop table

-- DROP TABLE telemetry_event_config;

CREATE TABLE telemetry_event_config ( id int4 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1 NO CYCLE) NOT NULL, event_type varchar(50) NOT NULL, parameters jsonb NULL, is_enabled bool DEFAULT true NOT NULL, description text NULL, CONSTRAINT telemetry_event_config_pkey PRIMARY KEY (id));


-- public.geozone_event_config определение

-- Drop table

-- DROP TABLE geozone_event_config;

CREATE TABLE geozone_event_config ( id int4 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1 NO CYCLE) NOT NULL, geozone_id int8 NOT NULL, event_type varchar(20) NOT NULL, is_enabled bool DEFAULT true NOT NULL, CONSTRAINT geozone_event_config_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['enter'::character varying, 'leave'::character varying])::text[]))), CONSTRAINT geozone_event_config_pkey PRIMARY KEY (id), CONSTRAINT geozone_event_config_geozone_id_fkey FOREIGN KEY (geozone_id) REFERENCES geozones(id));
CREATE INDEX idx_geozone_event_config_geozone_id ON public.geozone_event_config USING btree (geozone_id);


-- public.vehicles определение

-- Drop table

-- DROP TABLE vehicles;

CREATE TABLE vehicles ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, "name" varchar(100) NOT NULL, registration_number varchar(20) NOT NULL, device_id varchar(50) NOT NULL, type_id int4 NOT NULL, department_id int4 NULL, created_at timestamp DEFAULT now() NOT NULL, CONSTRAINT vehicles_device_id_key UNIQUE (device_id), CONSTRAINT vehicles_pkey PRIMARY KEY (id), CONSTRAINT vehicles_department_id_fkey FOREIGN KEY (department_id) REFERENCES departments(id));
CREATE INDEX idx_vehicles_department_id ON public.vehicles USING btree (department_id);


-- public.telemetry_packets определение

-- Drop table

-- DROP TABLE telemetry_packets;

CREATE TABLE telemetry_packets ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, vehicle_id int8 NOT NULL, packet_time timestamp NOT NULL, reception_time timestamp DEFAULT now() NOT NULL, latitude float8 NULL, longitude float8 NULL, s2_cell varchar(20) NULL, CONSTRAINT telemetry_packets_pkey PRIMARY KEY (id), CONSTRAINT telemetry_packets_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES vehicles(id));
CREATE INDEX idx_telemetry_packets_packet_time ON public.telemetry_packets USING btree (packet_time);
CREATE INDEX idx_telemetry_packets_s2_cell ON public.telemetry_packets USING btree (s2_cell);
CREATE INDEX idx_telemetry_packets_vehicle_id ON public.telemetry_packets USING btree (vehicle_id);
CREATE INDEX idx_telemetry_packets_vehicle_time ON public.telemetry_packets USING btree (vehicle_id, packet_time);


-- public.telemetry_subrecords определение

-- Drop table

-- DROP TABLE telemetry_subrecords;

CREATE TABLE telemetry_subrecords ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, packet_id int8 NOT NULL, record_type int2 NOT NULL, altitude float4 NULL, speed float4 NULL, course float4 NULL, odometer float4 NULL, sensors_data jsonb NULL, "timestamp" timestamp NULL, CONSTRAINT telemetry_subrecords_pkey PRIMARY KEY (id), CONSTRAINT telemetry_subrecords_packet_id_fkey FOREIGN KEY (packet_id) REFERENCES telemetry_packets(id));
CREATE INDEX idx_telemetry_subrecords_packet_id ON public.telemetry_subrecords USING btree (packet_id);


-- public.geozone_events определение

-- Drop table

-- DROP TABLE geozone_events;

CREATE TABLE geozone_events ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, vehicle_id int8 NOT NULL, geozone_id int8 NOT NULL, event_type varchar(20) NOT NULL, event_time timestamp NOT NULL, subrecord_id int8 NULL, CONSTRAINT geozone_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['enter'::character varying, 'leave'::character varying])::text[]))), CONSTRAINT geozone_events_pkey PRIMARY KEY (id), CONSTRAINT geozone_events_geozone_id_fkey FOREIGN KEY (geozone_id) REFERENCES geozones(id), CONSTRAINT geozone_events_subrecord_id_fkey FOREIGN KEY (subrecord_id) REFERENCES telemetry_subrecords(id), CONSTRAINT geozone_events_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES vehicles(id));
CREATE INDEX idx_geozone_events_event_time ON public.geozone_events USING btree (event_time);
CREATE INDEX idx_geozone_events_geozone_id ON public.geozone_events USING btree (geozone_id);
CREATE INDEX idx_geozone_events_subrecord_id ON public.geozone_events USING btree (subrecord_id);
CREATE INDEX idx_geozone_events_vehicle_id ON public.geozone_events USING btree (vehicle_id);


-- public.telemetry_events определение

-- Drop table

-- DROP TABLE telemetry_events;

CREATE TABLE telemetry_events ( id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL, vehicle_id int8 NOT NULL, event_type varchar(50) NOT NULL, event_time timestamp NOT NULL, value float4 NULL, threshold float4 NULL, subrecord_id int8 NULL, config_id int4 NOT NULL, CONSTRAINT telemetry_events_pkey PRIMARY KEY (id), CONSTRAINT telemetry_events_config_id_fkey FOREIGN KEY (config_id) REFERENCES telemetry_event_config(id), CONSTRAINT telemetry_events_subrecord_id_fkey FOREIGN KEY (subrecord_id) REFERENCES telemetry_subrecords(id), CONSTRAINT telemetry_events_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES vehicles(id));
CREATE INDEX idx_telemetry_events_config_id ON public.telemetry_events USING btree (config_id);
CREATE INDEX idx_telemetry_events_event_time ON public.telemetry_events USING btree (event_time);
CREATE INDEX idx_telemetry_events_subrecord_id ON public.telemetry_events USING btree (subrecord_id);
CREATE INDEX idx_telemetry_events_vehicle_id ON public.telemetry_events USING btree (vehicle_id);