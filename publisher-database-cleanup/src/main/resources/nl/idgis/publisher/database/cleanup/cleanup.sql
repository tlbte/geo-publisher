begin;

with job_ids as (
	select distinct ij.job_id from publisher.import_job ij
	where (ij.id < (select last_imported_id
			from (select dataset_id, max(ij2.id) as last_imported_id
				from publisher.import_job ij2
				join publisher.job_state js on js.job_id = ij2.job_id
				where js.state = 'SUCCEEDED'
				group by dataset_id) as info
			where info.dataset_id = ij.dataset_id))
), notification_ids as (select n.id as notification_id from publisher.notification n where n.job_id in (select job_id from job_ids))
delete from publisher.notification_result nr where nr.notification_id in (select notification_id from notification_ids);

with job_ids as (
	select distinct ij.job_id from publisher.import_job ij
	where (ij.id < (select last_imported_id
			from (select dataset_id, max(ij2.id) as last_imported_id
				from publisher.import_job ij2
				join publisher.job_state js on js.job_id = ij2.job_id
				where js.state = 'SUCCEEDED'
				group by dataset_id) as info
			where info.dataset_id = ij.dataset_id))
)
delete from publisher.notification n where n.job_id in (select job_id from job_ids);

with job_ids as (
	select distinct ij.job_id from publisher.import_job ij
	where (ij.id < (select last_imported_id
			from (select dataset_id, max(ij2.id) as last_imported_id
				from publisher.import_job ij2
				join publisher.job_state js on js.job_id = ij2.job_id
				where js.state = 'SUCCEEDED'
				group by dataset_id) as info
			where info.dataset_id = ij.dataset_id))
)
delete from publisher.job j where j.id in (select job_id from job_ids);

commit;