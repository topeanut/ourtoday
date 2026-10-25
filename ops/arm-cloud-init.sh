#!/bin/bash
# ARM 서버 첫 부팅 시: 8080 열기 + Docker 설치 (앱 이관은 이후 수동)
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT
netfilter-persistent save
apt-get update -y && DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 rsync
systemctl enable --now docker && usermod -aG docker ubuntu
touch /home/ubuntu/BOOTSTRAP_DONE
