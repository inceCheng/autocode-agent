from enum import Enum


class ProjectType(str, Enum):
    HTML = "HTML"
    MULTI_FILE = "MULTI_FILE"
    VUE_PROJECT = "VUE_PROJECT"
