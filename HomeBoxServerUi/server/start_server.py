import subprocess
import os

def start_http_server():
    """Start the Python HTTP server on port 8087"""
    try:
        print("Changing directory to ~/wasmJs/productionExecutable and starting HTTP server on port 8087...")
        # Change directory to ~/wasmJs/productionExecutable
        os.chdir(os.path.expanduser('~/wasmJs/productionExecutable'))
        subprocess.Popen(["python3", "-m", "http.server", "8087"])
    except Exception as e:
        print(f"Error starting HTTP server: {e}")

def run_setup_script():
    """Run the setup-server-env.ssh script from ~/server/bin"""
    try:
        print("Changing directory to ~/server/bin and running setup-server-env.ssh script...")
        # Change directory to ~/server/bin
        os.chdir(os.path.expanduser('~/server/bin'))
        subprocess.run(["chmod", "+x", "setup-server-env.ssh"], check=True)
        subprocess.run(["./setup-server-env.ssh"], check=True)
    except Exception as e:
        print(f"Error running setup script: {e}")

def main():
    start_http_server()
    run_setup_script()

if __name__ == "__main__":
    main()
