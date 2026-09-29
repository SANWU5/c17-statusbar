# 内置苹方

原字体设计：Apple PingFang UI。可变字体重建：alphaArgon / ACT-02。

来源：[ACT-02/PingFangUI-VF](https://github.com/ACT-02/PingFangUI-VF)，版本 21.4d1e1。

模块内置文件是 SC 字体的裁剪版本：常规宽度固定为 100，保留真实 wght 字重轴 100–900。仅保留 161 个编码字符，包括 ASCII 数字、英文、标点、时间日期词和常见运营商文字。文件 60,364 字节。

输入其他中文字时，使用系统字体补齐；不把整个中文字库装进 APK。字体粗细由 wght 轴调整。各文字组分别设置字重。

内置路径：app/src/main/assets/fonts/PingFangSC-VF.ttf。

SHA-256：6ad793d8ba30cbbe76537efd92ed1493739a0a6295bab5292fc3549772e92538

字体导入支持 TTF、OTF、TTC，最大 96 MiB；TTC 使用首个字体。导入前核对文件头并实际尝试加载，复制到设备保护存储，之后替换原始文件不会破坏已导入的字体。字体读取接口仅供模块和 SystemUI 使用。

自选静态字体只有其文件本身的字形；连续真实字重需要可变字体的 wght 轴。
