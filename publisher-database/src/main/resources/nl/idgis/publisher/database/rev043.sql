
--
-- Add cascade delete to the relation between import job and job:
--

alter table publisher.import_job
drop constraint import_job_job_id_fkey,
add constraint import_job_job_id_fkey
	foreign key (job_id) 
	references publisher.job (id) match simple
	on update no action on delete cascade;

--
-- Add cascade delete to the relation between import job and import_job_column:
--

alter table publisher.import_job_column
drop constraint import_job_column_import_job_id_fkey,
add constraint import_job_column_import_job_id_fkey 
	foreign key (import_job_id)
	references publisher.import_job (id) match simple
	on update no action on delete cascade;

insert into publisher.version (id) values (43);
