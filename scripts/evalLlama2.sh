cd ..

python eval/evaluator.py \
--answer_dir=./results/llama2/one_shot/ \
--pattern_path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=llama \
--passk=1
