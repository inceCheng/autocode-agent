from pydantic import BaseModel, Field


class HtmlGenResponse(BaseModel):
    """HTML代码生成响应DTO，封装生成结果的关键信息"""

    filename: str = Field(..., description="生成的HTML文件名（含时间戳与UUID）")
    file_path: str = Field(..., description="HTML文件在服务器上的本地绝对路径")
    url_path: str = Field(..., description="HTML文件的HTTP访问路径，可直接在浏览器中打开")
    status: str = Field(..., description="执行状态：success 表示成功，error 表示失败")
