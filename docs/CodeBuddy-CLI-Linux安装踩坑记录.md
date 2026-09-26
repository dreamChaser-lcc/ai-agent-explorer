# CodeBuddy CLI 在 Linux 虚拟机上安装踩坑记录

记录时间：2026-09-25

环境信息：

- Ubuntu 系 Linux 虚拟机（主机名 `lccserver`，登录用户 `lcc`）
- 机器上没有 Node.js，也没有安装 Homebrew
- 最终安装版本：CodeBuddy Code 2.158.0，落盘位置 `~/.local/bin/codebuddy`

这份文档按「实际踩坑顺序」还原整个过程，最后给出可直接复制使用的可用方案，方便以后在新机器上少走弯路。

## 一、结论：目前可用的两种安装方式

### 方式一：官方原生安装脚本（推荐，不依赖 Node 和 Homebrew）

```bash
curl -fsSL https://copilot.tencent.com/cli/install.sh | bash
source ~/.bashrc
codebuddy --version
```

脚本的行为：检测系统架构 → 下载对应平台的二进制包 → 校验 SHA256 → 执行内置的 `codebuddy install` 安装到用户级目录。

当前实测的安装产物：

- 安装位置：`~/.local/bin/codebuddy`
- 安装结束时脚本会提示：`To apply PATH changes, restart your shell or run: source ~/.bashrc`
- 这一步提示非常关键，不执行就会出现「装成功但命令找不到」的假象，详见坑 4

稳妥一点可以先看脚本内容再执行：

```bash
curl -fsSL https://copilot.tencent.com/cli/install.sh -o /tmp/cb-install.sh
less /tmp/cb-install.sh
bash /tmp/cb-install.sh
```

### 方式二：npm 全局安装（机器上已有 Node 18+ 时）

```bash
node -v && npm -v
npm install -g @tencent-ai/codebuddy-code
codebuddy --version
```

该 npm 包提供的命令有：`codebuddy`、`codebuddy-code`、`cbc`（`cbc` 是短别名）。包体较大（解包约 170MB），并带 `node-pty` 等原生可选依赖，下载慢时建议切换国内源。

## 二、踩坑过程还原

### 坑 1：`codebuddy: command not found`（问题的起点）

现象：

```text
lcc@lccserver:~/code-ai$ codebuddy
codebuddy: command not found
```

这个报错的本质只有一个：**shell 在当前 PATH 里找不到名为 `codebuddy` 的可执行文件**。可能是没装、装了没链接、装了但所在目录不在 PATH。所以排查方向固定为三步：

```bash
# 1. 有没有 Node/npm（npm 方式的前提）
node -v
npm -v

# 2. 命令到底在不在磁盘上
command -v codebuddy
ls -l ~/.local/bin/codebuddy

# 3. 所在目录在不在 PATH 里
echo "$PATH" | tr ':' '\n' | grep -n 'local/bin'
```

### 坑 2：npm 包名是个陷阱，`codebuddy-cli` 不是官方包

网上不少教程写的是：

```bash
npm install -g codebuddy-cli        # 错误，不要用
```

这个包在 npm 上确实存在，但它只是一个版本号 1.0.0、解包后 976 字节、仅输出 `Hello, World!` 的占位包，和腾讯 CodeBuddy 没有任何关系，装了也不会有 `codebuddy` 命令。

官方 npm 包名是：

```text
@tencent-ai/codebuddy-code
```

### 坑 3：照抄 brew 命令，但 Linux 虚拟机上根本没有 brew

在 Linux 虚拟机上执行官方 tap 的安装命令：

```bash
brew tap Tencent-CodeBuddy/tap
brew install codebuddy-code
```

实际输出：

```text
Command 'brew' not found, did you mean:
  command 'qbrew' from deb qbrew (0.4.1-8build1)
  command 'brec' from deb bplay (0.991-10build1)
Try: sudo apt install <deb name>
```

原因：

- Homebrew 不是 Linux 发行版自带组件，Ubuntu 软件源里也没有 `brew`，提示中的 `qbrew`、`brec` 是完全无关的包，不要装
- 官方 tap（`Tencent-CodeBuddy/homebrew-tap`）的 formula 确实支持 macOS 和 Linux，安装后会产出 `codebuddy` 和短别名 `cbc`，但前提是机器上已经装好了 Homebrew

如果确实想在 Linux 上用 Homebrew 方式安装，需要先补齐一整套环境（需要 sudo、编译依赖，并且要能访问 GitHub）：

```bash
sudo apt update && sudo apt install -y build-essential procps curl file git
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

echo 'eval "$(/home/linuxbrew/.linuxbrew/bin/brew shellenv)"' >> ~/.bashrc
eval "$(/home/linuxbrew/.linuxbrew/bin/brew shellenv)"

brew install Tencent-CodeBuddy/tap/codebuddy-code
```

结论：只为装一个 CLI，不值得在 Linux 虚拟机上引入 Homebrew，直接用方式一或方式二。

### 坑 4：安装脚本显示成功，但 `codebuddy` 依然 command not found

安装脚本执行成功的输出：

```text
→ Downloading CodeBuddy Code 2.158.0
→ Installing to ~/.local/bin

✓ CodeBuddy Code 2.158.0 installed successfully
  Location: ~/.local/bin/codebuddy

! To apply PATH changes, restart your shell or run:
    source ~/.bashrc
```

紧接着执行 `codebuddy -v` 仍然报：

```text
codebuddy: command not found
```

原因：安装器把 PATH 配置写进了 `~/.bashrc`，但**配置文件的改动不会自动作用于已经打开的终端会话**，而 `~/.local/bin` 又没有默认包含在 PATH 里，所以当前 shell 依然找不到命令。

解决办法（任选其一）：

```bash
# 方式一：重新加载配置（安装器提示的做法）
source ~/.bashrc
codebuddy --version

# 方式二：只对当前会话临时生效
export PATH="$HOME/.local/bin:$PATH"
codebuddy --version

# 方式三：先用绝对路径验证二进制本身没问题
~/.local/bin/codebuddy --version
```

持久化建议：同时写入 `~/.bashrc` 和 `~/.profile`，避免 SSH 登录 shell 场景下不生效。

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.profile
source ~/.bashrc
```

之后重新登录一次 SSH（或执行 `exec bash -l`）验证是否持久生效。

## 三、PATH 问题通用排查清单

适用于任何「命令装好了但提示 command not found」的场景：

```bash
# 1. 文件是否真的存在、是否有执行权限
ls -l ~/.local/bin/codebuddy

# 2. shell 配置里有没有对应的 PATH 语句
grep -n 'local/bin' ~/.bashrc ~/.profile 2>/dev/null

# 3. 当前会话的 PATH 是否已包含该目录
echo "$PATH" | tr ':' '\n' | grep -n 'local/bin'

# 4. 让当前会话立即生效
source ~/.bashrc

# 5. 换一个全新的登录 shell 再验证
exec bash -l
```

关于 Ubuntu 下 `~/.bashrc` 与 `~/.profile` 的加载差异：

- 交互式非登录 shell（新开一个终端窗口）读取 `~/.bashrc`
- 登录 shell（SSH 登录）先读 `~/.profile`，再通过 `~/.profile` 中的逻辑间接加载 `~/.bashrc`
- 两边都写上 PATH，可以规避大多数「换个终端就找不到命令」的问题

## 四、常见报错速查表

| 报错 / 现象 | 原因 | 处理方式 |
| --- | --- | --- |
| `codebuddy: command not found` | 没装 / 没链接 / PATH 不含所在目录 | 按第三节清单逐项排查 |
| `Command 'brew' not found` | Linux 上没装 Homebrew | 改用官方安装脚本或 npm |
| `npm: command not found` | 机器上没有 Node.js | 安装 Node.js LTS（推荐 nvm）或改用官方安装脚本 |
| 装完提示成功但命令仍找不到 | `~/.local/bin` 未加入 PATH | `source ~/.bashrc` 或手动追加 PATH |
| 新开终端命令又消失 | 只改了当前会话，没写进配置文件 | 写入 `~/.bashrc` 与 `~/.profile` |
| `SHA256 mismatch` | 下载不完整或被代理改写 | 删除缓存重新下载 |
| 安装脚本下载失败 | 网络受限 | 配置代理后重试，或改用 npm 方式 |
| `EACCES` / `Permission denied` | 全局目录属主不对 | 不要用 sudo 装 npm 包，改用 nvm 或修正目录属主 |

## 五、关键事实备忘

- 官方 npm 包：`@tencent-ai/codebuddy-code`，提供命令 `codebuddy`、`codebuddy-code`、`cbc`
- 官方 Homebrew tap：`Tencent-CodeBuddy/tap`，formula 名 `codebuddy-code`，支持 macOS 与 Linux，但必须先有 Homebrew
- 官方原生安装脚本：`https://copilot.tencent.com/cli/install.sh`，默认安装到 `~/.local/bin/codebuddy`
- Windows 对应的安装脚本：`https://www.codebuddy.cn/cli/install.ps1`
- 下载源：腾讯云 COS 广州节点（`acc-1258344699.cos.ap-guangzhou.myqcloud.com`），脚本会做 SHA256 校验
- 实测 Linux x86_64（glibc）制品校验值：`a0b8bb6ebf59b739b808c13aa55a8a6241318d98cf212b7def2ccfe6ab11360e`（对应 2.158.0 版本，可用于核对下载完整性）
