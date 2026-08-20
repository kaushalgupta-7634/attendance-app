# test_qr.py
import urllib.request
import json
import urllib.error

base_url = "http://localhost:8080"

def post_json(url, data, headers=None):
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=json.dumps(data).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code} for POST {url}: {e.read().decode('utf-8', errors='ignore')}")
        raise

def get_bytes(url, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, response.headers.get("Content-Type"), response.read()
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code} for GET {url}: {e.read().decode('utf-8', errors='ignore')}")
        raise

try:
    # 1. Login
    print("Logging in...")
    passwords = ["password123", "pass123", "password", "pass"]
    token = None
    for pwd in passwords:
        try:
            print(f"Trying password: {pwd}...")
            status, login_res = post_json(f"{base_url}/auth/login", {"username": "teacher1", "password": pwd})
            token = login_res.get("accessToken")
            if not token:
                print(f"login_res did not contain accessToken: {login_res}")
            print(f"Success! Login response status: {status}, Token obtained: {token[:15]}...")
            break
        except Exception as e:
            print(f"Failed with password {pwd}. Error: {e}")
            import traceback
            traceback.print_exc()
    
    if not token:
        raise Exception("Failed to login with any of the candidate passwords.")

    # 2. Start Session
    print("Starting session...")
    status, session_res = post_json(
        f"{base_url}/sessions/start",
        {
            "className": "CS101",
            "subject": "Computer Science",
            "startTime": None,
            "endTime": None,
            "classroomLat": 0.0,
            "classroomLng": 0.0,
            "radiusMeters": 500.0
        },
        headers={"Authorization": f"Bearer {token}"}
    )
    session_id = session_res.get("id")
    is_active = session_res.get("active")
    print(f"Session start status: {status}, Session ID: {session_id}, Active: {is_active}")

    # 3. Get QR Code
    print(f"Fetching QR code for session {session_id}...")
    status, content_type, content_bytes = get_bytes(
        f"{base_url}/sessions/{session_id}/qr",
        headers={"Authorization": f"Bearer {token}"}
    )
    print(f"QR Response status: {status}")
    print(f"Content-Type: {content_type}")
    print(f"Content size: {len(content_bytes)} bytes")

except Exception as e:
    print(f"Exception occurred: {e}")
