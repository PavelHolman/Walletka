// Entry point for Cloudflare Workers. All logic is Kotlin (../src/*Main), compiled by `npm run build`.
import { fetch } from "../build/kotlin/pass-service.mjs";

export default { fetch };
