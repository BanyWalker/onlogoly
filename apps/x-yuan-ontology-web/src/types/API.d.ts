declare namespace API {
	// 分页参数
	interface PageParams {
		pageNum: number;
		pageSize: number;
	}

	// 分页结果
	interface PageResult<T> {
		current: number;
		size: number;
		total: number;
		pages: number;
		records: T[];
	}

	interface POST {
		id: number;
		title: string;
		content: string;
		author: string;
		createdAt: string;
	}

	interface HttpResponse {
		message: MessageParamsWithType;
		data: unknown;
		code: number;
		resultData: T | undefined;
		resultMsg?: string;
	}
}
