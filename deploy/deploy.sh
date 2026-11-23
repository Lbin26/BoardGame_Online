#!/usr/bin/env bash
# DiceGo 一键部署 / 更新脚本（在阿里云服务器上以 root 运行）
#   sudo bash deploy.sh
# 可通过环境变量覆盖：APP_DIR、REPO、BRANCH
#   REPO=https://github.com/Lbin26/BoardGame_Online.git BRANCH=main sudo -E bash deploy.sh
#
# 前置条件（需自行安装）：JDK 21、Node.js 20+、npm、Nginx、MySQL 8、Redis
# 首次运行前请先建库导表，并把 deploy/application-prod.yml.example 配置到 $APP_DIR/config/application-prod.yml

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/dicego}"
REPO="${REPO:-https://github.com/Lbin26/BoardGame_Online.git}"
BRANCH="${BRANCH:-main}"
SRC_DIR="$APP_DIR/src"
SERVER_DIR="$APP_DIR/server"
WWW_DIR="$APP_DIR/www"
CONFIG_DIR="$APP_DIR/config"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m[ERROR] %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 或 sudo 运行"

log "检查运行环境"
for cmd in git node npm java nginx systemctl curl; do
  command -v "$cmd" >/dev/null 2>&1 || die "缺少命令：$cmd"
done
java -version 2>&1 | head -1 | grep -q '21' || die "需要 JDK 21，当前：$(java -version 2>&1 | head -1)"
echo "环境检查通过：$(java -version 2>&1 | head -1)"

log "获取源码：$REPO ($BRANCH)"
if [ -d "$SRC_DIR/.git" ]; then
  git -C "$SRC_DIR" fetch origin "$BRANCH"
  git -C "$SRC_DIR" checkout -B "$BRANCH" "origin/$BRANCH"
  # 部署目录是专用副本，直接对齐远端，避免本地残留文件影响构建
  git -C "$SRC_DIR" reset --hard "origin/$BRANCH"
else
  mkdir -p "$SRC_DIR"
  git clone -b "$BRANCH" "$REPO" "$SRC_DIR"
fi

if [ ! -f "$CONFIG_DIR/application-prod.yml" ]; then
  mkdir -p "$CONFIG_DIR"
  cp "$SRC_DIR/deploy/application-prod.yml.example" "$CONFIG_DIR/application-prod.yml"
  chown -R dicego:dicego "$CONFIG_DIR" 2>/dev/null || true
  chmod 600 "$CONFIG_DIR/application-prod.yml"
  die "首次部署：已生成 $CONFIG_DIR/application-prod.yml，请先修改数据库/Redis 密码和 jwt-secret-key，再重新运行本脚本"
fi

log "构建后端"
mkdir -p "$SERVER_DIR/temp"
if command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  MVN="$SRC_DIR/mvnw"
  chmod +x "$MVN"
fi
(cd "$SRC_DIR" && "$MVN" -Pprod -DskipTests clean package)
[ -f "$SRC_DIR/ruoyi-admin/target/ruoyi-admin.jar" ] || die "后端 jar 构建失败"

log "构建前端"
(cd "$SRC_DIR/boardgame-ui" && npm ci && npm run build)
[ -f "$SRC_DIR/boardgame-ui/dist/index.html" ] || die "前端构建失败"

log "安装产物"
if [ -f "$SERVER_DIR/dicego.jar" ]; then
  cp -f "$SERVER_DIR/dicego.jar" "$SERVER_DIR/dicego.jar.bak.$(date +%Y%m%d%H%M%S)"
  # 最多保留 5 个历史包，方便回滚
  ls -1t "$SERVER_DIR"/dicego.jar.bak.* 2>/dev/null | tail -n +6 | xargs -r rm -f
fi
cp -f "$SRC_DIR/ruoyi-admin/target/ruoyi-admin.jar" "$SERVER_DIR/dicego.jar"
rm -rf "$WWW_DIR"
mkdir -p "$WWW_DIR"
cp -r "$SRC_DIR/boardgame-ui/dist/." "$WWW_DIR/"

id dicego >/dev/null 2>&1 || useradd -r -s /sbin/nologin dicego
chown -R dicego:dicego "$SERVER_DIR" "$WWW_DIR" "$CONFIG_DIR"
chmod 600 "$CONFIG_DIR/application-prod.yml"

log "安装 systemd 服务"
cp -f "$SRC_DIR/deploy/dicego.service" /etc/systemd/system/dicego.service
systemctl daemon-reload
systemctl enable dicego
systemctl restart dicego

log "安装 Nginx 站点"
cp -f "$SRC_DIR/deploy/nginx-dicego.conf" /etc/nginx/conf.d/dicego.conf
nginx -t && systemctl reload nginx

log "等待服务启动"
for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "后端已就绪"
    break
  fi
  sleep 2
done
systemctl is-active --quiet dicego || die "服务启动失败，请执行：journalctl -u dicego -n 100 --no-pager"

echo ""
echo "部署完成："
echo "  前端目录：$WWW_DIR"
echo "  后端 jar：$SERVER_DIR/dicego.jar"
echo "  本地配置：$CONFIG_DIR/application-prod.yml"
echo "  服务状态：systemctl status dicego"
echo "  运行日志：journalctl -u dicego -f"
