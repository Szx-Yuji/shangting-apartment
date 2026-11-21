import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {Client,status} from './lib/http-client.mjs';
const admin=new Client(),user=new Client();
status(await admin.request('POST','/api/admin/auth/login',{username:'admin',password:'Admin123!2026'}),200,'admin login');
status(await user.request('POST','/api/auth/login',{username:'tenant',password:'Tenant123!2026'}),200,'tenant login');
const identity=status(await user.request('GET','/api/session'),200,'identity');
const apt=status(await admin.request('GET','/api/admin/catalog/apartments'),200,'apartments').records[0];
const room=status(await admin.request('POST','/api/admin/catalog/rooms',{
 apartmentId:apt.id,roomNumber:`C${Date.now().toString(36)}`,title:'并发验证房源',monthlyRent:2300,
 area:32,bedrooms:1,orientation:'南',description:'独立并发验证数据',imageUrl:'/images/room-2.svg',tags:[],published:true
}),201,'room');
const start=new Date(Date.now()+3600_000),end=new Date(start.getTime()+1800_000);
const slot=status(await admin.request('POST','/api/admin/appointment-slots',{
 roomId:room.id,startTime:start.toISOString(),endTime:end.toISOString(),capacity:2
}),201,'slot');
const attempts=await Promise.all(Array.from({length:6},()=>user.request('POST','/api/me/appointments',{
 slotId:slot.id,contactName:'并发验证',contactPhone:'13800000000'
},{'Idempotency-Key':randomUUID()})));
assert.equal(attempts.filter(x=>x.status===201).length,2,'capacity must allow exactly two; statuses='+attempts.map(x=>x.status+':'+(x.body?.detail||'ok')).join('|'));
assert.ok(attempts.every(x=>x.status===201||x.status===409),'other attempts must be conflicts');
const slots=()=>admin.request('GET',`/api/admin/appointment-slots?roomId=${room.id}`);
assert.equal(status(await slots(),200,'slot occupancy').records[0].reservedCount,2);
const booking=attempts.find(x=>x.status===201).body;
const cancels=await Promise.all([user.request('POST',`/api/me/appointments/${booking.id}/cancel`),user.request('POST',`/api/me/appointments/${booking.id}/cancel`)]);
cancels.forEach(x=>status(x,204,'concurrent cancel'));
assert.equal(status(await slots(),200,'one released').records[0].reservedCount,1);
const remaining=attempts.filter(x=>x.status===201)[1].body;
const race=await Promise.all([
 admin.request('PATCH',`/api/admin/appointments/${remaining.id}/status`,{status:'REJECTED'}),
 user.request('POST',`/api/me/appointments/${remaining.id}/cancel`)
]);
assert.equal(race.filter(x=>x.status===204).length,1,'only one transition wins');
assert.equal(race.filter(x=>x.status===409).length,1,'other transition conflicts');
assert.equal(status(await slots(),200,'all capacity released').records[0].reservedCount,0);
const begin=new Date();begin.setUTCDate(begin.getUTCDate()+1);const finish=new Date(begin);finish.setUTCMonth(finish.getUTCMonth()+2);
const lease={roomId:room.id,userId:identity.account.id,startDate:begin.toISOString().slice(0,10),endDate:finish.toISOString().slice(0,10),deposit:2300};
const contracts=await Promise.all(Array.from({length:4},()=>admin.request('POST','/api/admin/leases',lease)));
assert.equal(contracts.filter(x=>x.status===201).length,1,'one overlapping contract created');
assert.ok(contracts.every(x=>x.status===201||x.status===409));
const id=contracts.find(x=>x.status===201).body.id;
await Promise.all([admin.request('POST',`/api/admin/leases/${id}/activate`),admin.request('POST',`/api/admin/leases/${id}/activate`)]).then(xs=>xs.forEach(x=>status(x,200,'activate race')));
const bills=status(await user.request('GET','/api/me/bills?pageSize=100'),200,'bills').records.filter(x=>x.leaseId===id);
assert.equal(bills.length,1);
await Promise.all([user.request('POST',`/api/me/bills/${bills[0].id}/pay`),user.request('POST',`/api/me/bills/${bills[0].id}/pay`)]).then(xs=>xs.forEach(x=>assert.equal(status(x,200,'payment race').status,'PAID')));
status(await user.request('POST','/api/me/appointments',{
 slotId:slot.id,contactName:'已出租房源验证',contactPhone:'13800000000'
},{'Idempotency-Key':randomUUID()}),409,'active lease blocks booking');
status(await admin.request('POST',`/api/admin/leases/${id}/terminate`),200,'finish synthetic lease');
status(await admin.request('PATCH',`/api/rooms/${room.id}/publication`,{published:false}),204,'hide synthetic listing');
console.log('PASS: MySQL concurrent capacity, repeated cancellation, cancel/reject race, overlapping leases, activation and payment idempotency.');
