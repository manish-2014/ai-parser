# Gitea LXC Server Setup on Proxmox

This document outlines the complete process for setting up a self-hosted Gitea server inside a Debian LXC container on a Proxmox VE host. The setup is designed to be efficient, secure, and easy to manage for a homelab environment.

---

## Final Container Specifications

| Attribute        | Value                                   | Notes                                       |
| ---------------- | --------------------------------------- | ------------------------------------------- |
| **Hostname** | `gitea`                                 | Resolvable on the local network via `hosts` file. |
| **Operating System**| Debian 12 "Bookworm"                    | Standard minimal template.                  |
| **CPU Cores** | 2 Cores                                 | Adjustable as needed.                       |
| **Memory** | 2048 MB (2 GB)                          | Includes a buffer for large Git operations. |
| **Disk Size** | 40 GB                                   | Sized to accommodate a large LFS repository. |
| **IP Address** | `192.168.3.106` (Static)                | Reserved via DHCP on the router.            |
| **Database** | SQLite3                                 | Simple, file-based, no separate service needed. |

---

## 1. LXC Container Creation

The foundation of the setup is a Proxmox LXC container created with the following key settings in the Proxmox Web UI:

- **Template:** `debian-12-standard` downloaded from Proxmox's template repository.
- **Privileged:** No. Created as an **unprivileged container** for enhanced security.
- **Storage:** Root disk created on `local-lvm`.
- **Network:** Configured to use DHCP on the `vmbr0` bridge.

## 2. Network Configuration

To ensure a stable and convenient connection, the following network configuration was performed:

1.  **Find MAC Address:** The container's MAC address (`bc:24:11:1c:97:d0`) was identified using `ip a` in the LXC console.
2.  **DHCP Reservation:** A reservation was created on the home router to permanently assign the IP address `192.168.3.106` to the container's MAC address.
3.  **Local DNS Resolution:** The `hosts` file on the desktop computer was edited to resolve the name `gitea` to the container's IP.
    - **Path:** `/etc/hosts` (macOS/Linux) or `c:\Windows\System32\drivers\etc\hosts` (Windows).
    - **Entry:** `192.168.3.106  gitea`

## 3. Base Container Setup (SSH Access)

To allow for easy remote management, an SSH server was installed and configured inside the container.

1.  **Install SSH Server:**
    ```bash
    apt update
    apt install openssh-server -y
    ```
2.  **Configure Root Login:** The SSH configuration file was edited to permit password-based login for the `root` user during setup.
    - **File:** `/etc/ssh/sshd_config`
    - **Change:** The line `#PermitRootLogin prohibit-password` was changed to `PermitRootLogin yes`.
3.  **Restart Service:** The SSH service was restarted to apply the new configuration.
    ```bash
    systemctl restart sshd
    ```

## 4. Gitea Installation (from Binary)

Gitea was installed using the officially recommended binary method for a clean, self-contained setup. All commands were run as `root` inside the container via SSH.

1.  **Install Git:**
    ```bash
    apt install git -y
    ```
2.  **Create Dedicated `git` User:** A system user was created to run the Gitea service securely.
    ```bash
    adduser --system --shell /bin/bash --group --disabled-password --home /home/git git
    ```
3.  **Create Directories:** The required directory structure for Gitea was created and permissions were set.
    ```bash
    mkdir -p /var/lib/gitea/{custom,data,log}
    chown -R git:git /var/lib/gitea/
    chmod -R 750 /var/lib/gitea/
    mkdir /etc/gitea
    chown root:git /etc/gitea
    chmod 770 /etc/gitea
    ```
4.  **Download and Install Binary:** The latest Gitea binary was downloaded and moved to its final location.
    ```bash
    wget -O /tmp/gitea [https://dl.gitea.com/gitea/1.21.11/gitea-1.21.11-linux-amd64](https://dl.gitea.com/gitea/1.21.11/gitea-1.21.11-linux-amd64)
    mv /tmp/gitea /usr/local/bin/gitea
    chmod +x /usr/local/bin/gitea
    ```
5.  **Set up Systemd Service:** A service file was downloaded to allow Gitea to be managed by `systemd` and start on boot.
    ```bash
    wget [https://raw.githubusercontent.com/go-gitea/gitea/main/contrib/systemd/gitea.service](https://raw.githubusercontent.com/go-gitea/gitea/main/contrib/systemd/gitea.service) -P /etc/systemd/system/
    ```
6.  **Enable and Start Gitea:** The service was enabled and started.
    ```bash
    systemctl enable --now gitea
    ```

## 5. Gitea Web Configuration

The final setup was completed through the Gitea web interface.

1.  **URL:** `http://gitea:3000`
2.  **Database:** Selected **SQLite3** for simplicity and ease of management.
3.  **Administrator:** An initial admin user account (`manish`) was created.
4.  **Base URL:** The Gitea Base URL was confirmed as `http://gitea:3000`.

## Post-Setup: Using Git with SSH

To interact with repositories, SSH keys are used for authentication.

1.  A user's public SSH key (e.g., `~/.ssh/id_ed25519.pub`) is added to their account in the Gitea Web UI.
2.  All `git` operations (clone, push, pull) must use the `git` system user in the SSH URL. Gitea uses the provided SSH key to map the connection to the correct Gitea user.
    - **Correct URL Format:** `git@gitea:username/repository-name.git`
    - **Example:** `git clone git@gitea:manish/test-repo.git`

---
*This document was generated on October 9, 2025.*