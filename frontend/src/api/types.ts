export interface DocumentSummary {
  id: string;
  name: string;
  description: string | null;
  contentType: string | null;
  sizeBytes: number;
  lastModifiedDate: string;
  owner: string | null;
  sharedWith: string[];
}

export interface SearchParams {
  nameLike: string;
  descriptionLike: string;
  dateFrom: string;
  dateTo: string;
}

export const emptySearchParams: SearchParams = {
  nameLike: '',
  descriptionLike: '',
  dateFrom: '',
  dateTo: '',
};
