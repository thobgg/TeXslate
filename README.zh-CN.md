<p align="center">
  <img src="./docs/wordmark.png" alt="TeXslate" width="440">
</p>

<p align="center"><strong>Android 上的原生 LaTeX/XeTeX 编辑器 —— 以平板为先。</strong></p>

<p align="center">🇬🇧 <a href="./README.md">English</a> · 🇩🇪 <a href="./README.de.md">Deutsch</a> · 🇨🇳 <strong>简体中文</strong></p>

> ⚠️ **本页为草稿翻译，尚未经母语者校对。** 欢迎指正与改进 —— 见
> [中文本地化的 issue](https://github.com/thobgg/TeXslate/issues)。

在平板上直接写 LaTeX，右侧实时看到 PDF —— 不需要终端、不需要云服务、不需要另一台
电脑。TeXslate 把编辑器、**设备上的编译器**（Tectonic/XeTeX）和 PDF 预览放进同一个
原生 Android 界面。没有账号，没有联网要求：除首次编译需联网获取一次 TeX 宏包外，
全部离线运行。

## 中文文档

**支持中文排版。** `ctex` 与 `xeCJK` 均可使用；模板中写死的 Windows 字体
（`SimSun`、`黑体`、`KaiTi`、`Microsoft YaHei` 等）会自动替换为 TeX 宏包中自带的
**Fandol** 字体，因此不依赖设备是否安装了 CJK 系统字体。你的源文件不会被修改 ——
只有本次编译使用的工作副本会被改写，并且每一次替换都会明确提示。

已用 **[CUMCMThesis](https://github.com/latexstudio/CUMCMThesis)**（全国大学生数学
建模竞赛的 LaTeX 论文模板）实测：在 Galaxy Tab S8 Ultra 上完整编译通过，承诺书页、
表格与公式均正常排版。

## 主要功能

- **设备上编译**（Tectonic/XeTeX），可选择输入时自动编译
- **平板分屏**：左侧编辑器，右侧实时 PDF；手机上则以标签页切换
- **多文件项目**：项目文件夹侧边栏、`\input`/`\include`、参考文献
- **参考文献**：`bibtex`；`biblatex` 可用 `backend=bibtex`，thesis 版另附
  **biber 2.17**（交叉编译的 Perl 运行时），可直接使用 `backend=biber`
- **索引**：`\printindex` 可用，索引由应用自行生成
- **来自电脑的文档也能编译**：缺失字体自动替换、`inputenc` 与驱动选项自动适配、
  Latin-1 文件正确读取、文件名大小写自动匹配、EPS 图片以占位框代替
- 语法高亮、查找与替换、文档大纲、模板与向导
- **可选的 AI 助手**（默认关闭，需自备 API 密钥）

## 安装

APK 见 [Releases](https://github.com/thobgg/TeXslate/releases) —— 请选择
**arm64-v8a**（真机）。推荐使用 [Obtainium](https://github.com/ImranR98/Obtainium)
以获得自动更新。

目前处于 **alpha 阶段**：可用于真实文档，但仍在快速变化。

## 参与测试

如果你用 LaTeX 写论文、幻灯片或参赛论文，欢迎在自己的设备上试用，并告诉我什么地方
出了问题 —— 尤其欢迎非三星设备、手机，以及 Android 8–10 的反馈。请提交
[issue](https://github.com/thobgg/TeXslate/issues)，中文或英文皆可。

许可证：[GPL-3.0-or-later](./LICENSE)。
