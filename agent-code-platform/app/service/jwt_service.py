import base64
import logging

import jwt

logger = logging.getLogger(__name__)


class JwtService:
    """JWT令牌校验服务，与Java后端JwtUtils保持一致的HS256验签逻辑"""

    def __init__(self, secret: str, algorithm: str = "HS256") -> None:
        # Java端JJWT使用Decoders.BASE64.decode()解码密钥，
        # 该解码器对不完整Base64采用宽松策略：丢弃末尾无法构成完整4字符组的残余字节。
        # 例如 "ABCd==" (5 data chars, 5%4=1) 会被截断为 "ABC" (3 data chars) 再解码。
        self._key = self._decode_jjwt_base64(secret)
        self._algorithm = algorithm

    @staticmethod
    def _decode_jjwt_base64(secret: str) -> bytes:
        """模拟JJWT Decoders.BASE64.decode()的宽松Base64解码行为。

        JJWT解码器在遇到数据长度%4==1时，会丢弃末尾1个字符使其成为合法Base64。
        标准Python base64.b64decode则会直接抛出异常。
        """
        data = secret.rstrip("=")
        remainder = len(data) % 4
        if remainder == 1:
            # JJWT宽松行为：截断末尾1个不完整字符
            data = data[:-1]
            remainder = 0
        pad = "=" * ((4 - remainder) % 4) if remainder else ""
        try:
            return base64.b64decode(data + pad)
        except Exception:
            # 非Base64格式，按UTF-8原始字节处理
            return secret.encode("utf-8")

    def validate_token(self, token: str) -> dict:
        """
        校验JWT令牌的签名和有效期。

        Args:
            token: JWT令牌字符串（不含"Bearer "前缀）

        Returns:
            解码后的Claims字典

        Raises:
            jwt.ExpiredSignatureError: 令牌已过期
            jwt.InvalidTokenError: 令牌无效
        """
        return jwt.decode(token, self._key, algorithms=[self._algorithm])

    def extract_user_id(self, token: str) -> str:
        """
        从JWT令牌中提取用户ID（Subject字段）。

        Args:
            token: JWT令牌字符串（不含"Bearer "前缀）

        Returns:
            用户ID字符串
        """
        claims = self.validate_token(token)
        return claims.get("sub", "")
