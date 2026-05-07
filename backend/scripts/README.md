Доступ к БД: localhost 5432 user:user password:password 

CREATE TABLE public.geozones (
	id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
	"name" text NOT NULL,
	"type" varchar(10) NOT NULL,
	coordinates jsonb NOT NULL,
	is_active bool DEFAULT true NOT NULL,
	s2_key int8 DEFAULT 0 NOT NULL,
	lat float8 DEFAULT 0 NOT NULL,
	lon float8 DEFAULT 0 NOT NULL,
	CONSTRAINT geozones_pkey PRIMARY KEY (id),
	CONSTRAINT geozones_type_check CHECK (((type)::text = ANY ((ARRAY['polygon'::character varying, 'circle'::character varying])::text[])))
);