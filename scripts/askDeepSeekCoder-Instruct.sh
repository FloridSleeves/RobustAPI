cd ..

GPU=$1

python askHF.py \
--ckpt_path deepseek-ai/deepseek-coder-6.7b-instruct \
--tokenizer_path deepseek-ai/deepseek-coder-6.7b-instruct \
--result_dir ./results/deepseek-instruct/ \
--shot_number 1 \
--gpu $GPU
