# Keystore 签名密钥信息

此目录用于放置 TinaIDE 的本地 Release 签名密钥库。

## 密钥库信息

- **文件**: `release.jks`
- **类型**: PKCS12
- **算法**: RSA 2048-bit
- **有效期**: 10000 天
- **别名 (Alias)**: `tinaide`

## 证书 DN 信息

| 字段 | 值 |
|------|-----|
| CN (Common Name) | TinaIDE |
| OU (Organization Unit) | Tina |
| O (Organization) | WuXiangGujun |
| L (Locality) | Nanchang |
| ST (State) | Jiangxi |
| C (Country) | CN |

## 注意事项

- `release.jks` 文件只保留在本机或 GitHub Actions 运行时，不要提交明文到仓库
- 密码存储在项目根目录的 `keystore.properties` 中（同样不应提交）
- 仓库只保留 `keystore.properties.example` 作为模板
- GitHub Actions release 构建会从仓库 Secrets 还原签名文件与运行时配置
- 如需重新生成，请使用以下命令：

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias tinaide \
  -dname "CN=TinaIDE, OU=Tina, O=WuXiangGujun, L=Nanchang, ST=Jiangxi, C=CN"
```

