# OpenAPI definition


**简介**:OpenAPI definition


**HOST**:http://localhost:8123/api/v1


**联系人**:


**Version**:v0


**接口路径**:/api/v1/v3/api-docs/default


[TOC]






# user-controller


## updateUser


**接口地址**:`/api/v1/user/update`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "userName": "",
  "userAvatar": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userUpdateRequest|UserUpdateRequest|body|true|UserUpdateRequest|UserUpdateRequest|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## update


**接口地址**:`/api/v1/user/update`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "userAccount": "",
  "userPassword": "",
  "userName": "",
  "userAvatar": "",
  "userProfile": "",
  "userRole": "",
  "editTime": "",
  "createTime": "",
  "updateTime": "",
  "isDelete": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|user|User|body|true|User|User|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||
|&emsp;&emsp;editTime|||false|string(date-time)||
|&emsp;&emsp;createTime|||false|string(date-time)||
|&emsp;&emsp;updateTime|||false|string(date-time)||
|&emsp;&emsp;isDelete|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK||


**响应参数**:


暂无


**响应示例**:
```javascript

```


## save


**接口地址**:`/api/v1/user/save`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "userAccount": "",
  "userPassword": "",
  "userName": "",
  "userAvatar": "",
  "userProfile": "",
  "userRole": "",
  "editTime": "",
  "createTime": "",
  "updateTime": "",
  "isDelete": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|user|User|body|true|User|User|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||
|&emsp;&emsp;editTime|||false|string(date-time)||
|&emsp;&emsp;createTime|||false|string(date-time)||
|&emsp;&emsp;updateTime|||false|string(date-time)||
|&emsp;&emsp;isDelete|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK||


**响应参数**:


暂无


**响应示例**:
```javascript

```


## userRegister


**接口地址**:`/api/v1/user/register`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "userAccount": "",
  "userPassword": "",
  "checkPassword": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userRegisterRequest|UserRegisterRequest|body|true|UserRegisterRequest|UserRegisterRequest|
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||
|&emsp;&emsp;checkPassword|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseLong|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## userLogout


**接口地址**:`/api/v1/user/logout`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## userLogin


**接口地址**:`/api/v1/user/login`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "userAccount": "",
  "userPassword": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userLoginRequest|UserLoginRequest|body|true|UserLoginRequest|UserLoginRequest|
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userPassword|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseLoginUserVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||LoginUserVO|LoginUserVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"id": 0,
		"userAccount": "",
		"userName": "",
		"userAvatar": "",
		"userProfile": "",
		"userRole": "",
		"createTime": "",
		"updateTime": ""
	},
	"message": ""
}
```


## listUserVOByPage


**接口地址**:`/api/v1/user/list/page/vo`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "pageNum": 0,
  "pageSize": 0,
  "sortField": "",
  "sortOrder": "",
  "id": 0,
  "userName": "",
  "userAccount": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userQueryRequest|UserQueryRequest|body|true|UserQueryRequest|UserQueryRequest|
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;sortField|||false|string||
|&emsp;&emsp;sortOrder|||false|string||
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponsePageUserVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||PageUserVO|PageUserVO|
|&emsp;&emsp;records||array|UserVO|
|&emsp;&emsp;&emsp;&emsp;id||integer||
|&emsp;&emsp;&emsp;&emsp;userAccount||string||
|&emsp;&emsp;&emsp;&emsp;userName||string||
|&emsp;&emsp;&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;&emsp;&emsp;userProfile||string||
|&emsp;&emsp;&emsp;&emsp;userRole||string||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;pageNumber||integer(int64)||
|&emsp;&emsp;pageSize||integer(int64)||
|&emsp;&emsp;totalPage||integer(int64)||
|&emsp;&emsp;totalRow||integer(int64)||
|&emsp;&emsp;optimizeCountQuery||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"records": [
			{
				"id": 0,
				"userAccount": "",
				"userName": "",
				"userAvatar": "",
				"userProfile": "",
				"userRole": "",
				"createTime": ""
			}
		],
		"pageNumber": 0,
		"pageSize": 0,
		"totalPage": 0,
		"totalRow": 0,
		"optimizeCountQuery": true
	},
	"message": ""
}
```


## deleteUser


**接口地址**:`/api/v1/user/delete`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deleteRequest|DeleteRequest|body|true|DeleteRequest|DeleteRequest|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||boolean||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": true,
	"message": ""
}
```


## addUser


**接口地址**:`/api/v1/user/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "userName": "",
  "userAccount": "",
  "userAvatar": "",
  "userProfile": "",
  "userRole": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userAddRequest|UserAddRequest|body|true|UserAddRequest|UserAddRequest|
|&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;userRole|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseLong|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||integer(int64)|integer(int64)|
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": 0,
	"message": ""
}
```


## page


**接口地址**:`/api/v1/user/page`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|page||query|true|PageUser|PageUser|
|&emsp;&emsp;records|||false|array|User|
|&emsp;&emsp;&emsp;&emsp;id|||false|integer||
|&emsp;&emsp;&emsp;&emsp;userAccount|||false|string||
|&emsp;&emsp;&emsp;&emsp;userPassword|||false|string||
|&emsp;&emsp;&emsp;&emsp;userName|||false|string||
|&emsp;&emsp;&emsp;&emsp;userAvatar|||false|string||
|&emsp;&emsp;&emsp;&emsp;userProfile|||false|string||
|&emsp;&emsp;&emsp;&emsp;userRole|||false|string||
|&emsp;&emsp;&emsp;&emsp;editTime|||false|string||
|&emsp;&emsp;&emsp;&emsp;createTime|||false|string||
|&emsp;&emsp;&emsp;&emsp;updateTime|||false|string||
|&emsp;&emsp;&emsp;&emsp;isDelete|||false|integer||
|&emsp;&emsp;pageNumber|||false|integer(int64)||
|&emsp;&emsp;pageSize|||false|integer(int64)||
|&emsp;&emsp;totalPage|||false|integer(int64)||
|&emsp;&emsp;totalRow|||false|integer(int64)||
|&emsp;&emsp;optimizeCountQuery|||false|boolean||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|PageUser|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|records||array|User|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userPassword||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;isDelete||integer(int32)||
|pageNumber||integer(int64)|integer(int64)|
|pageSize||integer(int64)|integer(int64)|
|totalPage||integer(int64)|integer(int64)|
|totalRow||integer(int64)|integer(int64)|
|optimizeCountQuery||boolean||


**响应示例**:
```javascript
{
	"records": [
		{
			"id": 0,
			"userAccount": "",
			"userPassword": "",
			"userName": "",
			"userAvatar": "",
			"userProfile": "",
			"userRole": "",
			"editTime": "",
			"createTime": "",
			"updateTime": "",
			"isDelete": 0
		}
	],
	"pageNumber": 0,
	"pageSize": 0,
	"totalPage": 0,
	"totalRow": 0,
	"optimizeCountQuery": true
}
```


## list


**接口地址**:`/api/v1/user/list`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|User|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|id||integer(int64)|integer(int64)|
|userAccount||string||
|userPassword||string||
|userName||string||
|userAvatar||string||
|userProfile||string||
|userRole||string||
|editTime||string(date-time)|string(date-time)|
|createTime||string(date-time)|string(date-time)|
|updateTime||string(date-time)|string(date-time)|
|isDelete||integer(int32)|integer(int32)|


**响应示例**:
```javascript
[
	{
		"id": 0,
		"userAccount": "",
		"userPassword": "",
		"userName": "",
		"userAvatar": "",
		"userProfile": "",
		"userRole": "",
		"editTime": "",
		"createTime": "",
		"updateTime": "",
		"isDelete": 0
	}
]
```


## getUserById


**接口地址**:`/api/v1/user/get`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||query|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseUser|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||User|User|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userPassword||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;editTime||string(date-time)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;isDelete||integer(int32)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"id": 0,
		"userAccount": "",
		"userPassword": "",
		"userName": "",
		"userAvatar": "",
		"userProfile": "",
		"userRole": "",
		"editTime": "",
		"createTime": "",
		"updateTime": "",
		"isDelete": 0
	},
	"message": ""
}
```


## getInfo


**接口地址**:`/api/v1/user/getInfo/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|User|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|id||integer(int64)|integer(int64)|
|userAccount||string||
|userPassword||string||
|userName||string||
|userAvatar||string||
|userProfile||string||
|userRole||string||
|editTime||string(date-time)|string(date-time)|
|createTime||string(date-time)|string(date-time)|
|updateTime||string(date-time)|string(date-time)|
|isDelete||integer(int32)|integer(int32)|


**响应示例**:
```javascript
{
	"id": 0,
	"userAccount": "",
	"userPassword": "",
	"userName": "",
	"userAvatar": "",
	"userProfile": "",
	"userRole": "",
	"editTime": "",
	"createTime": "",
	"updateTime": "",
	"isDelete": 0
}
```


## getUserVOById


**接口地址**:`/api/v1/user/get/vo`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||query|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseUserVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||UserVO|UserVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;createTime||string(date-time)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"id": 0,
		"userAccount": "",
		"userName": "",
		"userAvatar": "",
		"userProfile": "",
		"userRole": "",
		"createTime": ""
	},
	"message": ""
}
```


## getLoginUser


**接口地址**:`/api/v1/user/get/login`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseLoginUserVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||LoginUserVO|LoginUserVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userAccount||string||
|&emsp;&emsp;userName||string||
|&emsp;&emsp;userAvatar||string||
|&emsp;&emsp;userProfile||string||
|&emsp;&emsp;userRole||string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": {
		"id": 0,
		"userAccount": "",
		"userName": "",
		"userAvatar": "",
		"userProfile": "",
		"userRole": "",
		"createTime": "",
		"updateTime": ""
	},
	"message": ""
}
```


## remove


**接口地址**:`/api/v1/user/remove/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK||


**响应参数**:


暂无


**响应示例**:
```javascript

```


# health-controller


## healthCheck


**接口地址**:`/api/v1/health/`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|BaseResponseString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|data||string||
|message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"data": "",
	"message": ""
}
```