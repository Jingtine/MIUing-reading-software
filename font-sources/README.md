# 原始字库

`kinghwa.ttf` 是用户提供的 `京華老宋体v3.0.ttf` 原始文件，完整保留；不直接打入 APK。旧 Chromium69 拒绝大于30MiB的字体，原文件为33,259,644字节，故运行时使用三个 Unicode 分片，完整覆盖原文件全部已编码字符。

开发阶段安装 `fonttools==4.60.1`，运行 `python tools/fonts/build_kinghwa.py` 可重新生成 `app/src/main/assets/fonts/kinghwa-{text,cjk,extended}.ttf`。本工作区工具安装在忽略的 `.tools/font-tools`，运行时设置 `PYTHONPATH` 指向该目录。FontTools 仅供开发，不是应用依赖。

脚本校验原文件 SHA256、分片互斥范围、全字符覆盖、每个已编码字形的展开轮廓和横纵度量，以及各分片小于30MiB。生成报告见 `kinghwa-coverage.json`。字库原始版权字段保留，来源说明见 `app/src/main/assets/fonts/PROVENANCE.txt`。
