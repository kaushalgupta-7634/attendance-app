# test_concurrent_login.py
import urllib.request
import urllib.error
import json

base_url = "http://localhost:8080"

def post_json(url, data, headers=None):
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    req_data = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(url, data=req_data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code} for POST {url}: {e.read().decode('utf-8', errors='ignore')}")
        raise

def get_json(url, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', errors='ignore')

try:
    print("=== STARTING CONCURRENT LOGIN TEST ===")
    
    # 1. Login Session 1 (Simulated Device A)
    print("Device A: Logging in...")
    status, login_res = post_json(f"{base_url}/auth/login", {"username": "teacher1", "password": "password123"})
    token_a = login_res.get("accessToken")
    print(f"Device A: Login success. Token: {token_a[:15]}...")

    # 2. Test Session 1 API access
    print("Device A: Fetching dashboard session list...")
    status_a, res_a = get_json(f"{base_url}/sessions/my-sessions", {"Authorization": f"Bearer {token_a}"})
    print(f"Device A: Response status: {status_a}")
    assert status_a == 200, f"Expected 200, got {status_a}"

    # 3. Login Session 2 (Simulated Device B)
    print("\nDevice B: Logging in (same account)...")
    status, login_res2 = post_json(f"{base_url}/auth/login", {"username": "teacher1", "password": "password123"})
    token_b = login_res2.get("accessToken")
    print(f"Device B: Login success. Token: {token_b[:15]}...")

    # 4. Test Session 2 API access
    print("Device B: Fetching dashboard session list...")
    status_b, res_b = get_json(f"{base_url}/sessions/my-sessions", {"Authorization": f"Bearer {token_b}"})
    print(f"Device B: Response status: {status_b}")
    assert status_b == 200, f"Expected 200, got {status_b}"

    # 5. Test Session 1 API access again (Should be rejected with 401!)
    print("\nDevice A: Making API request again (should be invalidated)...")
    status_a_retry, res_a_retry = get_json(f"{base_url}/sessions/my-sessions", {"Authorization": f"Bearer {token_a}"})
    print(f"Device A (Retry): Response status: {status_a_retry}")
    print(f"Device A (Retry): Response body: {res_a_retry}")
    
    if status_a_retry in (401, 403):
        print(f"\nSUCCESS: Device A was successfully logged out/invalidated (received HTTP {status_a_retry})!")
    else:
        print(f"\nFAILURE: Device A was not invalidated (status is {status_a_retry}).")
        exit(1)

except Exception as e:
    print(f"Exception occurred: {e}")
    exit(1)
