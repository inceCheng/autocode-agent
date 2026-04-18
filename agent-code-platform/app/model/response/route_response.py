from pydantic import BaseModel, Field

from app.common.enums.project_type import ProjectType


class RouteResponse(BaseModel):
    project_type: ProjectType = Field(..., description="推断出的项目类型")
    reasoning: str = Field(..., description="简短的判断理由，不超过30个字")
