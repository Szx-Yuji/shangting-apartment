"""Real browser workflow against the isolated complete-project demo backend.

This creates synthetic demonstration records through the UI. Only loopback ports
5174 (Vite proxy) or 8082 (complete JAR) are accepted; never targets the original app.
"""
import os
import struct
import time
import zlib
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright

BASE = os.environ.get("FRONTEND_URL", "http://127.0.0.1:5174")
parsed = urlparse(BASE)
if parsed.hostname not in ("127.0.0.1", "localhost") or parsed.port not in (5174, 8082):
    raise RuntimeError("Live UI checks are restricted to the isolated local complete project")
SHOTS = Path(os.environ.get("FRONTEND_SCREENSHOTS", "/tmp/shangting-live-screenshots"))
SHOTS.mkdir(parents=True, exist_ok=True)


def png():
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name+data) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 24, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress((b"\0" + bytes([49, 136, 121])*32)*24)) + chunk(b"IEND", b"")


def main():
    suffix = str(int(time.time()))
    user, password = "uiqa_"+suffix, "UiDemoSecure123!"
    apartment_name, room_title = "界面联调公寓"+suffix, "界面联调阳光房"+suffix
    tomorrow = datetime.now()+timedelta(days=3)
    starts = tomorrow.replace(hour=10, minute=0, second=0, microsecond=0)
    ends = starts+timedelta(hours=1)
    with sync_playwright() as p:
        cached = Path.home()/"Library/Caches/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-mac-arm64/chrome-headless-shell"
        browser = p.chromium.launch(headless=True, executable_path=str(cached) if cached.exists() else None)
        page = browser.new_page(viewport={"width":1440,"height":1000}, timezone_id="Asia/Shanghai")
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))

        def goto(path):
            page.goto(BASE+"/#"+path)
            page.wait_for_load_state("networkidle")

        def admin_login():
            goto("/admin/login")
            page.get_by_label("账号", exact=False).fill(os.environ.get("DEMO_ADMIN_USERNAME", "admin"))
            page.get_by_label("密码", exact=True).fill(os.environ.get("DEMO_ADMIN_PASSWORD", "Admin123!2026"))
            page.get_by_role("button", name="进入工作台", exact=True).click()
            page.get_by_role("heading", name="你好，今天也把生活照顾好。").wait_for()

        def logout():
            with page.expect_response(lambda response: urlparse(response.url).path == "/api/session"):
                page.get_by_role("button", name="退出登录", exact=True).click()
            page.get_by_role("link", name="登录", exact=True).wait_for()

        try:
            goto("/")
            page.locator(".listing-card").first.wait_for()
            page.screenshot(path=str(SHOTS/"public-desktop.png"), full_page=True)
            admin_login()
            page.screenshot(path=str(SHOTS/"admin-desktop.png"), full_page=True)
            page.get_by_role("link", name="公寓管理", exact=True).click()
            page.get_by_role("button", name="新增公寓", exact=True).click()
            modal = page.get_by_role("dialog", name="新增公寓")
            modal.get_by_label("公寓名称", exact=True).fill(apartment_name)
            modal.get_by_label("所在城市", exact=True).fill("长春")
            modal.get_by_label("基础月租金（元）", exact=True).fill("2400")
            modal.get_by_label("公寓地址", exact=True).fill("演示路88号")
            modal.locator('input[type="file"]').set_input_files({"name":"qa-image.png","mimeType":"image/png","buffer":png()})
            modal.get_by_text("图片已上传，地址已自动填写", exact=True).wait_for()
            modal.get_by_label("公寓介绍", exact=True).fill("这是完整项目浏览器验证生成的演示公寓。")
            modal.get_by_role("button", name="保存", exact=True).click()
            modal.wait_for(state="hidden")
            row = page.get_by_role("row").filter(has_text=apartment_name)
            row.wait_for()
            row.get_by_role("link", name="查看房间", exact=True).click()
            apartment_id = page.url.split("apartmentId=")[1].split("&")[0]
            page.get_by_role("button", name="新增房间", exact=True).click()
            modal = page.get_by_role("dialog", name="新增房间")
            modal.get_by_label("房间号", exact=True).fill("UI101")
            modal.get_by_label("房源标题", exact=True).fill(room_title)
            modal.get_by_label("月租金（元）", exact=True).fill("2400")
            modal.get_by_label("保存后上架房间", exact=True).check()
            modal.get_by_role("button", name="保存", exact=True).click()
            modal.wait_for(state="hidden")
            row = page.get_by_role("row").filter(has_text=room_title)
            row.wait_for()
            row.get_by_role("button", name="下架", exact=True).click()
            row.get_by_role("button", name="上架", exact=True).wait_for()
            row.get_by_role("button", name="上架", exact=True).click()
            row.get_by_role("button", name="下架", exact=True).wait_for()
            row.get_by_role("link", name="安排UI101室时段", exact=True).click()
            room_id = page.url.split("roomId=")[1].split("&")[0]
            page.get_by_role("button", name="新增时段", exact=True).click()
            modal = page.get_by_role("dialog", name="新增看房时段")
            modal.get_by_label("开始时间", exact=True).fill(starts.strftime("%Y-%m-%dT%H:%M"))
            modal.get_by_label("结束时间", exact=True).fill(ends.strftime("%Y-%m-%dT%H:%M"))
            modal.get_by_role("button", name="创建时段", exact=True).click()
            modal.wait_for(state="hidden")
            page.get_by_text("3 个", exact=True).wait_for()
            logout()

            goto("/register")
            page.get_by_label("怎么称呼你", exact=True).fill("界面联调租客")
            page.get_by_label("账号", exact=False).fill(user)
            page.get_by_label("密码", exact=False).fill(password)
            page.get_by_role("button", name="注册并登录", exact=True).click()
            page.get_by_role("heading", name="每一次看房，都离家更近").wait_for()
            goto("/rooms/"+room_id)
            with page.expect_response(lambda response: urlparse(response.url).path == f"/api/me/favorites/{room_id}" and response.request.method == "PUT") as response_info:
                page.get_by_role("button", name="收藏房源", exact=True).click()
            assert response_info.value.status == 204, response_info.value.text()
            page.get_by_role("status").filter(has_text="已加入我的收藏").wait_for()
            page.locator(".slot").first.click()
            page.get_by_label("联系人", exact=True).fill("界面联调租客")
            page.get_by_label("联系电话", exact=True).fill("13800000001")
            with page.expect_response(lambda response: urlparse(response.url).path == "/api/me/appointments" and response.request.method == "POST") as response_info:
                page.get_by_role("button", name="确认预约", exact=True).click()
            response = response_info.value
            assert response.status == 201, response.text()
            appointment_id = response.json()["id"]
            page.get_by_role("heading", name="预约已提交", exact=True).wait_for()
            page.get_by_role("link", name="查看我的预约", exact=True).click()
            personal_row = page.get_by_role("row").filter(has_text=f"预约 #{appointment_id}")
            personal_row.get_by_text("待审核", exact=True).wait_for()
            personal_row.get_by_text(starts.strftime("%Y/%m/%d %H:%M"), exact=True).wait_for()
            goto("/me/favorites")
            page.get_by_role("heading", name=room_title, exact=True).wait_for()
            logout()

            admin_login()
            goto("/admin/appointments")
            page.get_by_label("房间编号", exact=True).fill(room_id)
            page.get_by_role("button", name="筛选", exact=True).click()
            row = page.get_by_role("row").filter(has_text=f"预约 #{appointment_id}")
            row.get_by_text(starts.strftime("%Y/%m/%d %H:%M"), exact=True).wait_for()
            row.get_by_role("button", name="确认", exact=True).click()
            page.get_by_role("dialog", name="确认看房预约？").get_by_role("button", name="确认预约", exact=True).click()
            row.get_by_text("已确认", exact=True).wait_for()
            row.get_by_role("button", name=f"查看预约{appointment_id}的审核记录", exact=True).click()
            page.get_by_role("dialog").get_by_text("审核管理员：#1", exact=True).wait_for()
            page.get_by_role("button", name="关闭对话框", exact=True).click()
            with page.expect_download() as download:
                page.get_by_role("button", name="导出筛选结果", exact=True).click()
            assert download.value.suggested_filename.endswith(".csv")

            goto("/admin/leases")
            page.get_by_role("button", name="新增租约", exact=True).click()
            modal = page.get_by_role("dialog", name="新增租约")
            modal.get_by_label("租住房间", exact=False).select_option(room_id)
            options = modal.get_by_label("租客账号", exact=False).locator("option").all()
            user_option = next(option for option in options if user in option.inner_text())
            modal.get_by_label("租客账号", exact=False).select_option(user_option.get_attribute("value"))
            modal.get_by_label("租期开始", exact=True).fill(tomorrow.strftime("%Y-%m-%d"))
            modal.get_by_label("租期结束", exact=True).fill((tomorrow+timedelta(days=31)).strftime("%Y-%m-%d"))
            modal.get_by_label("押金（元）", exact=True).fill("2400")
            modal.get_by_role("button", name="创建租约", exact=True).click()
            modal.wait_for(state="hidden")
            row = page.get_by_role("row").filter(has_text=apartment_name)
            row.get_by_role("button", name="激活", exact=True).click()
            page.get_by_role("dialog", name="激活租约？").get_by_role("button", name="确认操作", exact=True).click()
            row.get_by_text("履约中", exact=True).wait_for()
            logout()

            goto("/login")
            page.get_by_label("账号", exact=False).fill(user)
            page.get_by_label("密码", exact=True).fill(password)
            page.get_by_role("button", name="登录", exact=True).click()
            page.get_by_role("heading", name="每一次看房，都离家更近").wait_for()
            goto("/me/leases")
            page.get_by_role("row").filter(has_text=apartment_name).get_by_text("履约中", exact=True).wait_for()
            goto("/me/bills")
            page.get_by_role("row").filter(has_text=apartment_name).get_by_role("button", name="演示支付", exact=True).click()
            page.get_by_role("dialog", name="确认演示支付").get_by_role("button", name="确认演示支付", exact=True).click()
            page.get_by_role("row").filter(has_text=apartment_name).get_by_text("已支付", exact=True).wait_for()
            goto("/me/notifications")
            page.locator(".notification").first.wait_for()
            while page.get_by_role("button", name="标为已读", exact=True).count():
                buttons = page.get_by_role("button", name="标为已读", exact=True)
                previous_count = buttons.count()
                with page.expect_response(lambda response: urlparse(response.url).path == "/api/me/notifications" and response.request.method == "GET"):
                    buttons.first.click()
                expect(buttons).to_have_count(previous_count - 1)
            page.set_viewport_size({"width":390,"height":844})
            goto("/me/appointments")
            page.get_by_role("heading", name="每一次看房，都离家更近").wait_for()
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            page.screenshot(path=str(SHOTS/"personal-mobile.png"), full_page=True)
            page.set_viewport_size({"width":1440,"height":1000})
            logout()

            admin_login()
            goto("/admin/accounts")
            page.get_by_role("button", name="新增管理员", exact=True).click()
            modal = page.get_by_role("dialog", name="新增管理员")
            admin_user = "uiop_"+suffix
            modal.get_by_label("管理员账号", exact=False).fill(admin_user)
            modal.get_by_label("初始密码", exact=False).fill("UiOperatorSecure123!")
            modal.get_by_role("button", name="创建账号", exact=True).click()
            modal.wait_for(state="hidden")
            row = page.get_by_role("row").filter(has_text=admin_user)
            row.get_by_role("button", name="停用", exact=True).click()
            page.get_by_role("dialog", name="停用管理员？").get_by_role("button", name="确认操作", exact=True).click()
            row.get_by_text("已停用", exact=True).wait_for()
            goto("/admin/leases")
            row = page.get_by_role("row").filter(has_text=apartment_name)
            row.get_by_role("button", name="终止", exact=True).click()
            page.get_by_role("dialog", name="终止租约？").get_by_role("button", name="确认操作", exact=True).click()
            row.get_by_text("已终止", exact=True).wait_for()
            goto("/admin/rooms?apartmentId="+apartment_id)
            row = page.get_by_role("row").filter(has_text=room_title)
            row.get_by_role("button", name="下架", exact=True).click()
            row.get_by_role("button", name="上架", exact=True).wait_for()
            page.set_viewport_size({"width":390,"height":844})
            goto("/admin/appointments")
            page.get_by_role("heading", name="预约审核", exact=True).wait_for()
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            page.screenshot(path=str(SHOTS/"admin-mobile.png"), full_page=True)
            logout()
            page.set_viewport_size({"width":1440,"height":1000})
            goto("/")
            page.locator(".listing-card").first.wait_for()
            if page.get_by_role("button", name="关闭提示", exact=True).count():
                page.get_by_role("button", name="关闭提示", exact=True).click()
            page.screenshot(path=str(SHOTS/"public-desktop.png"), full_page=True)
            page.set_viewport_size({"width":390,"height":844})
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            page.screenshot(path=str(SHOTS/"public-mobile.png"), full_page=True)
            assert not errors, errors
            print("PASS: real browser catalog CRUD/upload/publication/slots, registration, favorite, booking, review/log/CSV, lease activation/termination, demo payment, notifications and administrator create/disable; mobile layouts and no uncaught errors")
        except Exception:
            page.screenshot(path=str(SHOTS/"failure.png"), full_page=True)
            print("Failure screenshot:", SHOTS/"failure.png")
            raise
        finally:
            browser.close()


if __name__ == "__main__":
    main()
