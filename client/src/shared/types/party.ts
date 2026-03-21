export type Party = {
    publicId: string;
    name: string;
    description?: string | null;
};

export type CreatePartyInput = {
    name: string;
    description?: string;
    memberCins: string[];
};
