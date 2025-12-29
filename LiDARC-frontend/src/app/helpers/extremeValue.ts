type MinMaxMode = 'min' | 'max';

export function getExtremeValue<T>(
    items: T[] | null | undefined,
    selector: (item: T) => number | null | undefined,
    mode: MinMaxMode = 'min'
): number | null {
    if (!items || items.length === 0) return null;

    const values = items
        .map(selector)
        .filter((v): v is number => v !== null && v !== undefined);

    if (!values.length) return null;

    return mode === 'min' ? Math.min(...values) : Math.max(...values);
}
