import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

import { Client, status } from './lib/http-client.mjs';

const guest=new Client(),admin=new Client(),user=new Client(),other=new Client();
status(await guest.request('GET','/actuator/health'),200,'health');
status(await guest.request('GET','/api/admin/dashboard'),401,'anonymous admin access rejected');
status(await guest.request('GET','/api/me/appointments'),401,'anonymous user access rejected');
const listings=status(await guest.request('GET','/api/public/listings?pageSize=2'),200,'public listings');
assert.ok(listings.total>=18);assert.equal(listings.records.length,2);
status(await admin.request('POST','/api/admin/auth/login',{username:'admin',password:'Admin123!2026'}),200,'admin login');
assert.equal(status(await admin.request('GET','/api/session'),200,'admin identity').role,'ADMIN');
const suffix=Date.now().toString(36);
const account=status(await user.request('POST','/api/auth/register',{username:`qa_${suffix}`,password:'SmokeUser123!',displayName:'集成验证用户'}),201,'user registration');
status(await other.request('POST','/api/auth/register',{username:`qb_${suffix}`,password:'SmokeUser123!',displayName:'归属验证用户'}),201,'second user');
assert.equal(status(await user.request('GET','/api/session'),200,'user identity').role,'USER');
status(await user.request('GET','/api/admin/dashboard'),403,'user cannot access admin');
status(await admin.request('GET','/api/me/appointments'),403,'admin cannot impersonate tenant');

const apartments=status(await admin.request('GET','/api/admin/catalog/apartments'),200,'admin apartments');
const room=status(await admin.request('POST','/api/admin/catalog/rooms',{
  apartmentId:apartments.records[0].id,roomNumber:`T${suffix}`,title:'集成验证房源',monthlyRent:2200,
  area:36,bedrooms:1,orientation:'南',description:'自动集成验证合成房源',imageUrl:'/images/room-1.svg',tags:['演示'],published:true
}),201,'create room');
const start=new Date(Date.now()+7200_000),end=new Date(start.getTime()+1800_000);
const slot=status(await admin.request('POST','/api/admin/appointment-slots',{roomId:room.id,startTime:start.toISOString(),endTime:end.toISOString(),capacity:1}),201,'create slot');
const intent=randomUUID();const booking={slotId:slot.id,contactName:'=SUM(1,1)',contactPhone:'13800000000'};
const first=status(await user.request('POST','/api/me/appointments',booking,{'Idempotency-Key':intent}),201,'book');
assert.equal(status(await user.request('POST','/api/me/appointments',booking,{'Idempotency-Key':intent}),201,'replay').id,first.id);
status(await other.request('POST','/api/me/appointments',booking,{'Idempotency-Key':intent}),409,'cross-user replay rejected');
status(await other.request('POST','/api/me/appointments',booking,{'Idempotency-Key':randomUUID()}),409,'capacity enforced');
status(await user.request('POST','/api/me/appointments',{...booking,contactName:'不同内容'},{'Idempotency-Key':intent}),409,'payload binding');
const own=status(await user.request('GET','/api/me/appointments'),200,'my appointments');assert.ok(own.records.some(x=>x.id===first.id));
assert.ok(!status(await other.request('GET','/api/me/appointments'),200,'other appointments').records.some(x=>x.id===first.id));
status(await other.request('POST',`/api/me/appointments/${first.id}/cancel`),404,'cannot cancel another user');
status(await user.request('POST',`/api/me/appointments/${first.id}/cancel`),204,'cancel own');
status(await user.request('POST',`/api/me/appointments/${first.id}/cancel`),204,'cancel idempotent');
const slots=status(await admin.request('GET',`/api/admin/appointment-slots?roomId=${room.id}`),200,'slot state');
assert.equal(slots.records.find(x=>x.id===slot.id).reservedCount,0);
const second=status(await user.request('POST','/api/me/appointments',booking,{'Idempotency-Key':randomUUID()}),201,'rebook released capacity');
status(await admin.request('PATCH',`/api/admin/appointments/${second.id}/status`,{status:'CONFIRMED'}),204,'review');
status(await admin.request('PATCH',`/api/admin/appointments/${second.id}/status`,{status:'REJECTED'}),409,'repeat review rejected');
assert.equal(status(await admin.request('GET',`/api/admin/appointments/${second.id}/review-log`),200,'audit').toStatus,'CONFIRMED');
status(await admin.request('GET',`/api/admin/appointments?roomId=${room.id}&status=CONFIRMED`),200,'appointment filters');

status(await user.request('PUT',`/api/me/favorites/${room.id}`),204,'favorite');
status(await user.request('PUT',`/api/me/favorites/${room.id}`),204,'favorite replay');
assert.ok(status(await user.request('GET','/api/me/favorites'),200,'favorite list').some(x=>x.id===room.id));
status(await user.request('DELETE',`/api/me/favorites/${room.id}`),204,'unfavorite');
const date=new Date();date.setUTCDate(date.getUTCDate()+1);const from=date.toISOString().slice(0,10);
date.setUTCMonth(date.getUTCMonth()+3);const to=date.toISOString().slice(0,10);
const lease=status(await admin.request('POST','/api/admin/leases',{roomId:room.id,userId:account.id,startDate:from,endDate:to,deposit:2200}),201,'create lease');
status(await admin.request('POST','/api/admin/leases',{roomId:room.id,userId:account.id,startDate:from,endDate:to,deposit:2200}),409,'overlapping lease rejected');
status(await admin.request('POST',`/api/admin/leases/${lease.id}/activate`),200,'activate lease');
status(await admin.request('POST',`/api/admin/leases/${lease.id}/activate`),200,'activate idempotent');
const bills=status(await user.request('GET','/api/me/bills'),200,'my bills').records.filter(x=>x.leaseId===lease.id);
assert.equal(bills.length,1,'one first bill');assert.equal(Number(bills[0].amount),4400,'first bill includes deposit');
status(await other.request('POST',`/api/me/bills/${bills[0].id}/pay`),404,'cannot pay other bill');
assert.equal(status(await user.request('POST',`/api/me/bills/${bills[0].id}/pay`),200,'demo payment').status,'PAID');
assert.equal(status(await user.request('POST',`/api/me/bills/${bills[0].id}/pay`),200,'payment idempotent').status,'PAID');
status(await admin.request('POST',`/api/admin/leases/${lease.id}/terminate`),200,'terminate lease');
const notifications=status(await user.request('GET','/api/me/notifications'),200,'notifications');assert.ok(notifications.length>0);
status(await user.request('PATCH',`/api/me/notifications/${notifications[0].id}/read`),204,'read notification');
const csv=await admin.request('GET',`/api/admin/appointments/export.csv?roomId=${room.id}`);
status(csv,200,'async CSV export');assert.ok(csv.body.includes("'=SUM(1,1)"),'formula escaped');
const invalidImage=new FormData();invalidImage.set('file',new Blob(['<script>invalid</script>'],{type:'image/png'}),'fake.png');
status(await admin.request('POST','/api/admin/catalog/uploads',invalidImage),400,'fake image rejected');
status(await admin.request('POST','/api/admin/maintenance/run'),200,'bounded scheduled work');
status(await admin.request('GET','/api/admin/dashboard'),200,'dashboard');
status(await admin.request('PATCH',`/api/rooms/${room.id}/publication`,{published:false}),204,'hide synthetic listing');
status(await user.request('POST','/api/auth/logout'),204,'logout');
status(await user.request('GET','/api/me/appointments'),401,'logged-out session rejected');
console.log('PASS: public browsing, roles, CSRF, user ownership, booking idempotency/capacity/cancel, audit, favorites, leases, bills, notifications, safe CSV/upload and maintenance.');
