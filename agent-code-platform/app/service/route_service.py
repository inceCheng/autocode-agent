import logging

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.common.enums.project_type import ProjectType
from app.config.settings import get_settings
from app.model.response.route_response import RouteResponse

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是一个专业的代码生成方案架构师。你需要仔细阅读用户的需求，严格依据设定的项目类型标准，选择最合适的生成方案。优先选择最轻量且能满足需求的方案。

可选的代码生成类型：
1. HTML - 适合简单的静态页面，单个 HTML 文件，包含内联 CSS 和 JS
2. MULTI_FILE - 适合简单的多文件静态页面，分离 HTML、CSS、JS 代码
3. VUE_PROJECT - 适合复杂的现代化前端项目

判断规则：
- 如果用户需求简单，只需要一个展示页面，选择 HTML
- 如果用户需要多个页面但不涉及复杂交互，选择 MULTI_FILE
- 如果用户需求复杂，涉及多页面、复杂交互、数据管理等，选择 VUE_PROJECT"""


class RouteService:
    def __init__(self) -> None:
        settings = get_settings()
        llm = ChatOpenAI(
            api_key=settings.route_api_key.get_secret_value() if settings.route_api_key else None,
            base_url=settings.route_codegen_base_url if settings.route_codegen_base_url else "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model=settings.route_codegen_model_name if settings.route_codegen_model_name else "qwen3.5-flash",
            temperature=settings.route_codegen_temperature if settings.route_codegen_temperature else 0.3,
            max_tokens=50,
            extra_body={"enable_thinking": False},
        )
        self._structured_llm = llm.with_structured_output(
            RouteResponse,
            method="function_calling",
        )

    async def route(self, prompt: str) -> RouteResponse:
        messages = [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=prompt),
        ]
        try:
            result = await self._structured_llm.ainvoke(messages)
            if result is None:
                logger.warning("智能路由解析结果为None，回退返回 HTML 类型")
                return RouteResponse(
                    project_type=ProjectType.HTML,
                    reasoning="模型输出解析失败，默认回退",
                )
            return result
        except Exception:
            logger.exception("智能路由调用大模型失败，回退返回 HTML 类型")
            return RouteResponse(
                project_type=ProjectType.HTML,
                reasoning="大模型调用失败，默认回退",
            )
