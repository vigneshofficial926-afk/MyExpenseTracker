import os
import sys
from http.server import HTTPServer, SimpleHTTPRequestHandler

class CustomHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        web_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'web')
        super().__init__(*args, directory=web_dir, **kwargs)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate')
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()

def run(host='127.0.0.1', port=8085):
    server_address = (host, port)
    httpd = HTTPServer(server_address, CustomHandler)
    print(f"Vignesh Expense Tracker web server running at http://{host}:{port}/")
    print(f"Serving files from: {os.path.join(os.path.dirname(os.path.abspath(__file__)), 'web')}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server.")
        httpd.server_close()

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8085
    run(port=port)
