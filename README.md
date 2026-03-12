# Codex Switcher JavaFX

`codex_switch` 是一个基于 JavaFX 的桌面工具，用于管理 Codex 相关配置、账号、会话与 VS Code 扩展状态。

## 功能概览

- 多账号切换与本地配置管理（`config.toml`、`opencode.json`）
- Codex CLI 检测与启动
- VS Code 扩展扫描、备份、补丁与修复
- Session 检索、导出、清理与继续会话
- Skill 扫描、导入、备份与删除
- OpenAI 状态与 GitHub 更新检查

## 环境要求

- JDK 21
- Maven 3.9+
- Windows PowerShell 7（推荐）

## 快速开始

1. 安装依赖并构建：

```powershell
mvn -q -DskipTests clean package -Djavafx.platform=win
```

2. 本地开发启动：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\run_dev.ps1
```

3. Windows 打包：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\build_win.ps1
```

## 产物目录

- `target/`：Maven 构建输出
- `dist/win/CodexSwitcher/`：`jpackage` 生成的 app-image
- `dist/win/*.exe`：安装包（检测到 WiX 时生成）

## 项目结构

- `src/main/java/com/codexswitcher/app`：应用入口与状态管理
- `src/main/java/com/codexswitcher/service`：业务服务层
- `src/main/java/com/codexswitcher/ui`：主界面与通用组件
- `src/main/java/com/codexswitcher/ui/page`：功能页面
- `src/main/resources/assets`：图标与静态资源
- `src/main/resources/css`：样式文件
