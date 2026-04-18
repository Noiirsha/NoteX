export const buildStaticImageUrl = (filename: string) => {
  return `/static/get_image?filename=${encodeURIComponent(filename)}`
}
