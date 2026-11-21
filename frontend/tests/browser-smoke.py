"""Deterministic browser checks with intercepted API responses, no database writes.

Run with the server helper documented in frontend/README.md. This checks frontend
behavior; the root project's end-to-end checks cover the real backend separately.
"""
import json
import os
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright

BASE = os.environ.get("FRONTEND_URL", "http://127.0.0.1:5174")
SCREENSHOTS = Path(os.environ.get("FRONTEND_SCREENSHOTS", "/tmp/shangting-frontend-screenshots"))
SCREENSHOTS.mkdir(parents=True, exist_ok=True)


def main():
    listings = [dict(id=i, apartmentId=1, apartmentName="尚庭·湖畔里", city="长春", address="净月大街 128 号", roomNumber=f"{i}01", title=["阳光一居，留给自己的舒适空间", "窗边的好时光 · 精装一居", "通透两居，和生活慢慢相处"][i % 3], monthlyRent=2200+i*100, area=32+i, bedrooms=1, orientation="南", imageUrl=f"/images/room-{i%3+1}.svg", tags=["独立卫浴", "近地铁", "家具齐全"], published=True, available=True, description="朝南采光，配套齐全，给生活留一点从容。") for i in range(1, 10)]
    state = {"role": None, "booking_keys": [], "writes": [], "csrf_gets": 0}
    appointments = [dict(id=1, roomId=1, apartmentName="尚庭·湖畔里", roomNumber="101", contactName="测试租客", contactPhone="13800000000", appointmentTime="2030-11-02T02:00:00Z", status="PENDING", createdAt="2030-11-01T02:00:00Z")]

    def page(records):
        return dict(records=records, total=len(records), page=1, pageSize=10)

    def handle(route):
        req = route.request
        path = urlparse(req.url).path
        method = req.method
        status = 200
        result = {}
        if method not in ("GET", "HEAD", "OPTIONS"):
            assert req.headers.get("x-csrf-token") == "qa-csrf", (method, path, "missing csrf")
            state["writes"].append((method, path))
        if path == "/api/csrf":
            state["csrf_gets"] += 1
            result = dict(token="qa-csrf", headerName="X-CSRF-TOKEN")
        elif path == "/api/session":
            role = state["role"]
            result = dict(authenticated=bool(role), role=role, account=dict(id=1, username="qa_account", displayName="测试租客") if role else None)
        elif path.endswith("/auth/login") or path == "/api/auth/register":
            state["role"] = "ADMIN" if "/admin/" in path else "USER"
            result = dict(id=1, username="qa_account", displayName="测试租客")
        elif path.endswith("/auth/logout"):
            state["role"] = None
            status = 204
        elif path == "/api/public/listings":
            result = page(listings)
        elif path.startswith("/api/public/listings/"):
            result = listings[int(path.rsplit("/", 1)[1])-1]
        elif "appointment-slots" in path:
            result = page([dict(id=1, roomId=1, startTime="2030-11-02T02:00:00Z", endTime="2030-11-02T03:00:00Z", capacity=3, reservedCount=0, remainingCapacity=3)])
        elif path == "/api/me/appointments" and method == "POST":
            state["booking_keys"].append(req.headers.get("idempotency-key"))
            if len(state["booking_keys"]) == 1:
                status, result = 503, dict(detail="模拟连接中断，请使用相同内容重试", status=503)
            else:
                status, result = 201, dict(id=7, status="PENDING")
        elif path.endswith("/appointments"):
            result = page(appointments)
        elif path == "/api/admin/dashboard":
            result = dict(apartmentCount=3, roomCount=24, publishedRoomCount=20, pendingAppointmentCount=4, activeLeaseCount=9, unpaidBillCount=2, monthlyRevenue=26800, cityStats=[dict(city="长春", roomCount=14), dict(city="杭州", roomCount=10)], recentAppointments=appointments)
        elif path == "/api/admin/catalog/apartments":
            result = page([dict(id=1, name="尚庭·湖畔里", city="长春", monthlyRent=2300, address="净月大街128号", description="便捷通勤，舒适生活。", imageUrl="/images/room-1.svg", roomCount=9)]) if method == "GET" else dict(id=2)
        elif path == "/api/admin/catalog/rooms":
            result = page(listings)
        elif path in ("/api/admin/users", "/api/admin/accounts"):
            result = page([dict(id=1, username="qa_account", displayName="测试租客", enabled=True, createdAt="2030-11-01T02:00:00Z")])
        elif path == "/api/me/favorites":
            result = listings[:2]
        elif path.endswith("/leases") or path.endswith("/bills"):
            result = page([])
        elif path == "/api/me/notifications":
            result = []
        else:
            status = 204 if method != "GET" else 404
            result = dict(detail="测试接口不存在", status=404)
        route.fulfill(status=status, content_type="application/json; charset=utf-8", body="" if status == 204 else json.dumps(result, ensure_ascii=False))

    with sync_playwright() as p:
        cached = Path.home()/"Library/Caches/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-mac-arm64/chrome-headless-shell"
        browser = p.chromium.launch(headless=True, executable_path=os.environ.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE", str(cached)) if cached.exists() else None)
        try:
            browser_page = browser.new_page(viewport={"width": 1440, "height": 1100}, device_scale_factor=1)
            errors = []
            browser_page.on("pageerror", lambda error: errors.append(str(error)))
            browser_page.route("**/api/**", handle)
            browser_page.goto(BASE)
            browser_page.wait_for_load_state("networkidle")
            assert browser_page.get_by_role("heading", name="下一站，理想生活").is_visible()
            assert browser_page.locator(".listing-card").count() == 9
            browser_page.screenshot(path=str(SCREENSHOTS/"public-desktop.png"), full_page=True)

            browser_page.goto(BASE+"/#/login")
            browser_page.get_by_label("账号", exact=False).fill("qa_user")
            browser_page.get_by_label("密码", exact=True).fill("qa-password-at-least-12")
            browser_page.get_by_role("button", name="登录", exact=True).click()
            browser_page.wait_for_url("**/#/me/appointments")
            browser_page.get_by_role("heading", name="每一次看房，都离家更近").wait_for()
            csrf_before = state["csrf_gets"]
            browser_page.evaluate("""async () => {
                const { api } = await import('/src/api.ts');
                await Promise.all([
                    api('/api/me/favorites/1', { method: 'PUT' }),
                    api('/api/me/favorites/2', { method: 'PUT' }),
                    api('/api/me/favorites/3', { method: 'PUT' }),
                ]);
                await api('/api/me/favorites/4', { method: 'PUT' });
            }""")
            assert state["csrf_gets"] == csrf_before + 1, "Concurrent first writes must share one CSRF request and reuse it"
            browser_page.goto(BASE+"/#/rooms/1")
            browser_page.wait_for_load_state("networkidle")
            browser_page.locator(".slot").first.click()
            browser_page.get_by_label("联系人", exact=True).fill("测试租客")
            browser_page.get_by_label("联系电话", exact=True).fill("13800000000")
            browser_page.get_by_role("button", name="确认预约", exact=True).click()
            browser_page.get_by_text("模拟连接中断，请使用相同内容重试").wait_for()
            browser_page.locator(".slot").first.click()
            browser_page.get_by_role("button", name="确认预约", exact=True).click()
            browser_page.get_by_role("heading", name="预约已提交", exact=True).wait_for()
            assert len(state["booking_keys"]) == 2 and state["booking_keys"][0] == state["booking_keys"][1] and state["booking_keys"][0]

            state["role"] = "ADMIN"
            browser_page.goto(BASE+"/#/admin")
            browser_page.reload()
            browser_page.wait_for_load_state("networkidle")
            browser_page.get_by_role("heading", name="你好，今天也把生活照顾好。").wait_for()
            browser_page.screenshot(path=str(SCREENSHOTS/"admin-desktop.png"), full_page=True)
            browser_page.get_by_role("link", name="公寓管理", exact=True).click()
            browser_page.get_by_role("button", name="新增公寓", exact=True).click()
            browser_page.get_by_role("dialog", name="新增公寓").wait_for()
            browser_page.get_by_label("公寓名称", exact=True).fill("新公寓")
            browser_page.get_by_label("所在城市", exact=True).fill("长春")
            browser_page.get_by_label("公寓地址", exact=True).fill("示范街道100号")
            browser_page.get_by_role("button", name="保存", exact=True).click()
            browser_page.get_by_role("dialog", name="新增公寓").wait_for(state="hidden")

            browser_page.set_viewport_size({"width":390,"height":844})
            browser_page.goto(BASE+"/#/admin/appointments")
            browser_page.wait_for_load_state("networkidle")
            browser_page.get_by_role("heading", name="预约审核", exact=True).wait_for()
            browser_page.get_by_role("button", name="确认", exact=True).first.wait_for()
            assert browser_page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            browser_page.screenshot(path=str(SCREENSHOTS/"admin-mobile.png"), full_page=True)
            state["role"] = None
            browser_page.goto(BASE)
            browser_page.reload()
            browser_page.wait_for_load_state("networkidle")
            assert browser_page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            browser_page.screenshot(path=str(SCREENSHOTS/"public-mobile.png"), full_page=True)
            assert not errors, errors
            print("PASS: desktop/mobile layout, login, shared concurrent CSRF initialization/cache, booking idempotency retry and admin create form; no uncaught browser errors")
        finally:
            browser.close()


if __name__ == "__main__":
    main()
