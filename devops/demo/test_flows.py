"""Run inside the isolated demo AI container, not against the user's local API.

Creates synthetic messages/images in the demo database. Does not delete data.
"""
import asyncio
import io
import json
import uuid

import httpx
from PIL import Image
from websockets.asyncio.client import connect

API1 = "http://api-1:8080"
API2 = "http://api-2:8080"
ORIGIN = "http://localhost:4280"


async def main():
    async with httpx.AsyncClient(timeout=30, trust_env=False) as client:
        async def request(method, base, path, token=None, **kwargs):
            headers = {"Authorization": f"Bearer {token}"} if token else {}
            result = await client.request(method, base + path, headers=headers, **kwargs)
            result.raise_for_status()
            return result.json()

        async def login(email, password, role):
            user = await request("POST", API1, "/api/auth/login", json={"email": email, "password": password})
            assert user["role"] == role
            me = await request("GET", API2, "/api/users/me", user["token"])
            assert me["email"] == email, "JWT must work across replicas"
            return user["token"]

        buyer = await login("buyer01@e-biessa.tn", "Buyer123!", "BUYER")
        seller = await login("seller01@e-biessa.tn", "Seller123!", "SELLER")
        await login("admin@e-biessa.tn", "Admin123!", "ADMIN")
        stranger = await login("buyer02@e-biessa.tn", "Buyer123!", "BUYER")
        print("PASS: buyer, seller, admin login and cross-replica JWT", flush=True)
        parts = await request("GET", API2, "/api/parts/my-listings", seller)
        assert parts
        marker = "demo-verification-" + uuid.uuid4().hex[:10]
        detail = await request("POST", API1, "/api/messages/conversations", buyer,
                               json={"partId": parts[0]["id"], "message": marker})
        conversation_id = detail["conversation"]["id"]
        path = f"/api/messages/conversations/{conversation_id}"

        async def ticket(token, base):
            return (await request("POST", base, "/api/messages/conversations/realtime-ticket", token))["ticket"]

        # Issue on one replica, consume on the other; both sockets on distinct servers.
        bt = await ticket(buyer, API2)
        st = await ticket(seller, API1)
        async with connect(f"ws://api-1:8080/ws/messages?ticket={bt}", origin=ORIGIN) as bws, \
                connect(f"ws://api-2:8080/ws/messages?ticket={st}", origin=ORIGIN) as sws:
            sent = await request("POST", API1, path + "/messages", buyer, json={"message": marker + " text"})
            for socket in (bws, sws):
                event = json.loads(await asyncio.wait_for(socket.recv(), 10))
                assert event["message"]["id"] == sent["id"]
            print("PASS: Redis ticket exchange and cross-replica WebSocket text delivery", flush=True)

            image = io.BytesIO()
            Image.new("RGB", (32, 32), "orange").save(image, format="PNG")
            response = await request("POST", API2, path + "/messages", seller,
                                     data={"message": marker + " image"},
                                     files={"files": ("demo.png", image.getvalue(), "image/png")})
            for socket in (bws, sws):
                event = json.loads(await asyncio.wait_for(socket.recv(), 10))
                assert event["message"]["id"] == response["id"]
            attachment = response["attachments"][0]["url"]
            assert attachment.startswith("/uploads/")
            for base in (API1, API2):
                media = await client.get(base + attachment)
                media.raise_for_status()
                assert media.content, "Shared image must exist on both replicas"
            for socket in (bws, sws):
                try:
                    unexpected = await asyncio.wait_for(socket.recv(), 1)
                    raise AssertionError("Unexpected duplicate socket event")
                except asyncio.TimeoutError:
                    pass
            print("PASS: image notification once on both sockets and shared upload access", flush=True)

        for base, token in ((API1, buyer), (API2, seller)):
            history = await request("GET", base, path, token)
            ids = [item["id"] for item in history["messages"]]
            assert ids.count(sent["id"]) == ids.count(response["id"]) == 1
        denied = await client.get(API1 + path, headers={"Authorization": f"Bearer {stranger}"})
        assert denied.status_code in (403, 404)
        print("PASS: persistent history, no duplicate messages, unrelated buyer denied", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
